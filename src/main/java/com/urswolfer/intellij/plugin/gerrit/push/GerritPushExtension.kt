/*
 * Copyright 2013-2014 Urs Wolfer
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
package com.urswolfer.intellij.plugin.gerrit.push

import com.google.inject.Inject
import com.intellij.openapi.components.NamedComponent
import com.intellij.openapi.diagnostic.Logger
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import git4idea.push.GitPushOperation
import javassist.*

/**
 * Since there are no entry points for modifying the push dialog without copying a lot of source, some modifications
 * to the Git push setting panel (where you can set an alternative remote branch) are done with byte-code modification
 * with javassist:
 *
 * * Some methods of GitPushSupport are overwritten in order to inject Gerrit push support.
 * * GerritPushExtensionPanel, GerritPushOptionsPanel and GerritPushTargetPanel get copied to the Git plugin class loader.
 *
 * @author Urs Wolfer
 */
// proxy class below is registered
class GerritPushExtension @Inject constructor(
    private val gerritSettings: GerritSettings,
    private val log: Logger
) : NamedComponent {
    @Inject
    fun initComponent() {
        try {
            val classPool = ClassPool.getDefault()

            val gitIdeaPluginClassLoader =
                GitPushOperation::class.java.classLoader // it must be a class which is not modified (loaded) by javassist later on
            val gerritPluginClassLoader = GerritPushExtensionPanel::class.java.classLoader
            classPool.appendClassPath(LoaderClassPath(gitIdeaPluginClassLoader))
            classPool.appendClassPath(LoaderClassPath(gerritPluginClassLoader))

            copyGerritPluginClassesToGitPlugin(classPool, gitIdeaPluginClassLoader)

            modifyGitBranchPanel(classPool, gitIdeaPluginClassLoader)
        } catch (e: Exception) {
            log.error("Failed to inject Gerrit push UI.", e)
        } catch (e: Error) {
            log.error("Failed to inject Gerrit push UI.", e)
        }
    }

    private fun modifyGitBranchPanel(classPool: ClassPool, classLoader: ClassLoader) {
        try {
            val pushToGerrit = gerritSettings.pushToGerrit
            val forceDefaultBranch = gerritSettings.forceDefaultBranch

            val gitPushSupportClass = classPool["git4idea.push.GitPushSupport"]
            val gerritPushOptionsPanelClass =
                classPool["com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel"]

            gitPushSupportClass.addField(
                CtField(gerritPushOptionsPanelClass, "gerritPushOptionsPanel", gitPushSupportClass),
                "new com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel($pushToGerrit,$forceDefaultBranch);"
            )

            val createOptionsPanelMethod = gitPushSupportClass.getDeclaredMethod("createOptionsPanel")
            createOptionsPanelMethod.setBody(
                "{" +
                        "gerritPushOptionsPanel.initPanel(mySettings.getPushTagMode(), git4idea.config.GitVersionSpecialty.SUPPORTS_FOLLOW_TAGS.existsIn(myVcs.getVersion()), git4idea.config.GitVersionSpecialty.PRE_PUSH_HOOK.existsIn(myVcs.getVersion()));" +
                        "return gerritPushOptionsPanel;" +
                        "}"
            )

            val createTargetPanelMethod = gitPushSupportClass.getDeclaredMethod("createTargetPanel")
            // GitPushSupport#createTargetPanel signature change in: https://github.com/JetBrains/intellij-community/commit/1ab27885afa82e46eba4715829c88f0de494b652
            if (createTargetPanelMethod.longName == "git4idea.push.GitPushSupport.createTargetPanel(git4idea.repo.GitRepository,git4idea.push.GitPushTarget)") {
                createTargetPanelMethod.setBody(
                    "{" +
                            "return new com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel(this, $1, $2, gerritPushOptionsPanel);" +
                            "}"
                )
            } else if (createTargetPanelMethod.longName == "git4idea.push.GitPushSupport.createTargetPanel(git4idea.repo.GitRepository,git4idea.push.GitPushSource,git4idea.push.GitPushTarget)") {
                createTargetPanelMethod.setBody(
                    "{" +
                            "return new com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel(this, $1, $3, gerritPushOptionsPanel);" +
                            "}"
                )
            }

            gitPushSupportClass.toClass(classLoader, GitPushOperation::class.java.protectionDomain)
            gitPushSupportClass.detach()
        } catch (e: CannotCompileException) {
            log.error("Failed to inject Gerrit push UI.", e)
        } catch (e: NotFoundException) {
            log.error("Failed to inject Gerrit push UI.", e)
        }
    }

    private fun copyGerritPluginClassesToGitPlugin(classPool: ClassPool, targetClassLoader: ClassLoader) {
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel")
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel")
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel\$Companion")
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel\$initBranch\$1")
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel")
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel\$Companion")
        loadClass(
            classPool,
            targetClassLoader,
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel\$ChangeActionListener"
        )
        loadClass(
            classPool,
            targetClassLoader,
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel\$ChangeTextActionListener"
        )
        loadClass(
            classPool,
            targetClassLoader,
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel\$SettingsStateActionListener"
        )
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.util.UrlUtils")
    }

    private fun loadClass(classPool: ClassPool, targetClassLoader: ClassLoader, className: String) {
        try {
            val loadedClass = classPool[className]
            loadedClass.toClass(targetClassLoader, GitPushOperation::class.java.protectionDomain)
            loadedClass.detach()
        } catch (e: CannotCompileException) {
            log.error("Failed to load class required for Gerrit push UI injections.", e)
        } catch (e: NotFoundException) {
            log.error("Failed to load class required for Gerrit push UI injections.", e)
        }
    }

    override fun getComponentName(): String {
        return "GerritPushExtension"
    }


    class Proxy : NamedComponent {
        private val delegate: GerritPushExtension = GerritModule.getInstance<GerritPushExtension>()

        override fun getComponentName(): String {
            return delegate.componentName
        }
    }
}
