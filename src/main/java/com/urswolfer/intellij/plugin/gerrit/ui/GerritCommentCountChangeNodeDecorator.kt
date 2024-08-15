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
package com.urswolfer.intellij.plugin.gerrit.ui

import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.CommentInfo
import com.google.gerrit.extensions.restapi.RestApiException
import com.google.inject.Inject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.urswolfer.gerrit.client.rest.GerritRestApi
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils
import java.util.*

/**
 * @author Thomas Forrer
 */
class GerritCommentCountChangeNodeDecorator @Inject constructor(private val selectedRevisions: SelectedRevisions) :
    GerritChangeNodeDecorator {
    @Inject
    private lateinit var gerritApi: GerritRestApi

    @Inject
    private lateinit var pathUtils: PathUtils

    @Inject
    private lateinit var gerritSettings: GerritSettings

    @Inject
    private lateinit var log: Logger

    private var selectedChange: ChangeInfo? = null
    private var comments = setupCommentsSupplier()
    private var drafts = setupDraftsSupplier()
    private var reviewed = setupReviewedSupplier()

    init {
        selectedRevisions.addObserver { o: Observable?, arg: Any ->
            if (selectedChange != null && selectedChange!!.id == arg) {
                refreshSuppliers()
            }
        }
    }

    private fun refreshSuppliers() {
        comments = setupCommentsSupplier()
        drafts = setupDraftsSupplier()
        reviewed = setupReviewedSupplier()
    }

    override fun decorate(
        project: Project,
        change: Change,
        component: SimpleColoredComponent,
        selectedChange: ChangeInfo?
    ) {
        val affectedFilePath = getAffectedFilePath(change)
        if (affectedFilePath != null) {
            val text = getNodeSuffix(project, affectedFilePath)
            if (text.isNotEmpty()) {
                component.append(" ($text)", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                component.repaint()
            }
        }
    }

    override fun onChangeSelected(project: Project?, selectedChange: ChangeInfo?) {
        this.selectedChange = selectedChange
        refreshSuppliers()
    }

    private fun getAffectedFilePath(change: Change): String? {
        val afterRevision = change.afterRevision
        if (afterRevision != null) {
            return afterRevision.file.path
        }
        val beforeRevision = change.beforeRevision
        if (beforeRevision != null) {
            return beforeRevision.file.path
        }
        return null
    }

    private fun getNodeSuffix(project: Project, affectedFilePath: String): String {
        val path = getRelativeOrAbsolutePath(project, affectedFilePath)
        val fileName = PathUtils.ensureSlashSeparators(path)
        val parts = mutableListOf<String>()

        val commentsMap = comments.value
        val commentsForFile = commentsMap[fileName]
        if (commentsForFile != null) {
            parts.add("${commentsForFile.size} comment${if (commentsForFile.size == 1) "" else "s"}")
        }

        val draftsMap = drafts.value
        val draftsForFile = draftsMap[fileName]
        if (draftsForFile != null) {
            parts.add("${draftsForFile.size} draft${if (draftsForFile.size == 1) "" else "s"}")
        }

        if (reviewed.value.contains(fileName)) {
            parts.add("reviewed")
        }

        return parts.joinToString(", ")
    }

    private fun getRelativeOrAbsolutePath(project: Project, absoluteFilePath: String): String {
        return pathUtils.getRelativeOrAbsolutePath(project, absoluteFilePath, selectedChange!!.project)
    }

    private fun setupCommentsSupplier(): Lazy<Map<String, List<CommentInfo>>> {
        return lazy {
            try {
                gerritApi.changes()
                    .id(selectedChange!!.id)
                    .revision(selectedRevisionId)
                    .comments()
            } catch (e: RestApiException) {
                log.warn(e)
                emptyMap()
            }
        }
    }

    private fun setupDraftsSupplier(): Lazy<Map<String, List<CommentInfo>>> {
        return lazy {
            if (!gerritSettings.isLoginAndPasswordAvailable) {
                return@lazy emptyMap()
            }
            try {
                gerritApi.changes()
                    .id(selectedChange!!.id)
                    .revision(selectedRevisionId)
                    .drafts()
            } catch (e: RestApiException) {
                log.warn(e)
                emptyMap()
            }
        }
    }

    private fun setupReviewedSupplier(): Lazy<Set<String?>> {
        return lazy {
            if (!gerritSettings.isLoginAndPasswordAvailable) {
                return@lazy emptySet()
            }
            try {
                gerritApi.changes()
                    .id(selectedChange!!.id)
                    .revision(selectedRevisionId)
                    .reviewed()
            } catch (e: RestApiException) {
                log.warn(e)
                emptySet()
            }
        }
    }

    private val selectedRevisionId: String?
        get() = selectedRevisions[selectedChange!!]
}
