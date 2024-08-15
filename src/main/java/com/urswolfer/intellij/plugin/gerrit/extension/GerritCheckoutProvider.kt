/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.urswolfer.intellij.plugin.gerrit.extension

import com.google.common.base.Function
import com.google.common.base.Strings
import com.google.common.collect.ImmutableSortedSet
import com.google.common.collect.Iterables
import com.google.common.collect.Ordering
import com.google.common.io.ByteStreams
import com.google.gerrit.extensions.client.ListChangesOption
import com.google.gerrit.extensions.common.ProjectInfo
import com.google.gerrit.extensions.restapi.RestApiException
import com.google.gerrit.extensions.restapi.Url
import com.google.inject.Inject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.LocalFileSystem
import com.urswolfer.gerrit.client.rest.GerritRestApi
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.util.*
import git4idea.checkout.GitCheckoutProvider
import git4idea.checkout.GitCloneDialog
import git4idea.commands.Git
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.FileOutputStream

/**
 * Parts based on org.jetbrains.plugins.github.GithubCheckoutProvider
 *
 * @author oleg
 * @author Urs Wolfer
 */
class GerritCheckoutProvider @Inject constructor(
    private val localFileSystem: LocalFileSystem,
    private val gerritUtil: GerritUtil,
    private val gerritSettings: GerritSettings,
    private val log: Logger,
    private val notificationService: NotificationService,
    private val gerritApi: GerritRestApi
) : CheckoutProvider {
    override fun doCheckout(project: Project, listener: CheckoutProvider.Listener?) {
        if (!gerritUtil.testGitExecutable(project)) {
            return
        }
        FileDocumentManager.getInstance().saveAllDocuments()
        var availableProjects: List<ProjectInfo?>? = null
        try {
            availableProjects = gerritUtil.getAvailableProjects(project)
        } catch (e: Exception) {
            log.info(e)
            val notification = NotificationBuilder(
                project,
                "Couldn't get the list of Gerrit repositories",
                gerritUtil.getErrorTextFromException(e)
            )
            notificationService.notifyError(notification)
        }
        if (availableProjects == null) {
            return
        }
        val orderedProjects =
            ImmutableSortedSet.orderedBy(ID_REVERSE_ORDERING).addAll(availableProjects).build()

        val url = cloneBaseUrl

        val dialog = GitCloneDialog(project)
        for (projectInfo in orderedProjects) {
            dialog.prependToHistory(url + '/' + Url.decode(projectInfo!!.id))
        }
        dialog.show()
        if (!dialog.isOK) {
            return
        }
        dialog.rememberSettings()
        val destinationParent = localFileSystem.findFileByIoFile(File(dialog.parentDirectory)) ?: return
        val sourceRepositoryURL = dialog.sourceRepositoryURL
        val directoryName = dialog.directoryName
        val parentDirectory = dialog.parentDirectory

        val git = ApplicationManager.getApplication().getService(Git::class.java)

        val listenerWrapper = addCommitMsgHookListener(listener, directoryName, parentDirectory, project)

        GitCheckoutProvider.clone(
            project,
            git,
            listenerWrapper,
            destinationParent,
            sourceRepositoryURL,
            directoryName,
            parentDirectory
        )
    }

    override fun getVcsName(): String {
        return "Gerrit"
    }

    private val cloneBaseUrl: String?
        /**
         * If set, return the clone base url from the preferences. Otherwise, try to determine the Git clone url by
         * fetching a random change and processing its fetch url. If it fails, fall back to Gerrit host url config.
         *
         * This can be cleaned up once https://code.google.com/p/gerrit/issues/detail?id=2208 is implemented.
         */
        get() {
            if (!Strings.isNullOrEmpty(gerritSettings.cloneBaseUrl)) {
                return gerritSettings.cloneBaseUrlOrHost
            }
            var url = gerritSettings.host
            try {
                val changeInfos = gerritApi.changes().query()
                    .withLimit(1)
                    .withOption(ListChangesOption.CURRENT_REVISION)
                    .get()
                if (changeInfos.isEmpty()) {
                    log.info("ChangeInfo list is empty.")
                    return url
                }
                val changeInfo = Iterables.getOnlyElement(changeInfos)
                val fetchInfo = gerritUtil.getFirstFetchInfo(changeInfo)
                if (fetchInfo != null) {
                    val projectName = changeInfo.project
                    url = fetchInfo.url.replace("/$projectName$".toRegex(), "")
                }
            } catch (e: RestApiException) {
                log.info(e)
            }
            return url
        }

    /*
     * Since this is a listener which needs to be executed in any case, it cannot be a normal checkout-listener.
     * Checkout-listeners only get executed when "previous" listener got not executed (returns false).
     * Example: If user decides to setup a new project from newly created checkout, our listener does not get executed.
     */
    private fun addCommitMsgHookListener(
        listener: CheckoutProvider.Listener?,
        directoryName: String,
        parentDirectory: String,
        project: Project
    ): CheckoutProvider.Listener {
        return object : CheckoutProvider.Listener {
            override fun directoryCheckedOut(directory: File, vcs: VcsKey) {
                setupCommitMsgHook(parentDirectory, directoryName, project)

                listener?.directoryCheckedOut(directory, vcs)
            }

            override fun checkoutCompleted() {
                listener?.checkoutCompleted()
            }
        }
    }

    private fun setupCommitMsgHook(parentDirectory: String, directoryName: String, project: Project) {
        try {
            val commitMessageHook = gerritApi.tools().commitMessageHook
            val targetFile = File("$parentDirectory/$directoryName/.git/hooks/commit-msg")
            ByteStreams.copy(commitMessageHook, FileOutputStream(targetFile))
            targetFile.setExecutable(true)

            val notification = NotificationBuilder(
                project,
                "Gerrit Checkout done",
                "Commit-Message Hook has been set up."
            )
            notificationService.notify(notification)
        } catch (e: Exception) {
            log.info(e)
            val notification = NotificationBuilder(
                project,
                "Couldn't set up Gerrit Commit-Message Hook. Please do it manually.",
                gerritUtil.getErrorTextFromException(e)
            )
            notificationService.notifyError(notification)
        }
    }

    class Proxy : CheckoutProvider {
        private val delegate: CheckoutProvider = GerritModule.getInstance<GerritCheckoutProvider>()

        override fun doCheckout(project: Project, listener: CheckoutProvider.Listener?) {
            delegate.doCheckout(project, listener)
        }

        override fun getVcsName(): @NonNls String? {
            return delegate.vcsName
        }
    }

    companion object {
        private val GET_ID_FUNCTION = { from: ProjectInfo? -> from!!.id }
        private val ID_REVERSE_ORDERING: Ordering<ProjectInfo?> = Ordering.natural<Comparable<*>>().onResultOf(
            GET_ID_FUNCTION
        ).reverse()
    }
}
