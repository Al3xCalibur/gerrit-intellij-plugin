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
package com.urswolfer.intellij.plugin.gerrit.ui.diff

import com.google.gerrit.extensions.api.changes.DraftInput
import com.google.gerrit.extensions.client.Comment
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.CommentInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil

/**
 * @author Urs Wolfer
 */
// added with code
class CommentDoneAction(
    private val editor: Editor,
    private val commentsDiffTool: CommentsDiffTool,
    private val gerritUtil: GerritUtil,
    private val gerritSettings: GerritSettings,
    private val fileComment: Comment,
    private val changeInfo: ChangeInfo,
    private val revisionId: String?
) : AnAction("Done", null, AllIcons.Actions.Checked), DumbAware, UpdateInBackground {
    override fun actionPerformed(e: AnActionEvent) {
        val comment = DraftInput()
        comment.inReplyTo = fileComment.id
        comment.message = "Done"
        comment.line = fileComment.line
        comment.path = fileComment.path
        comment.side = fileComment.side
        comment.range = fileComment.range

        val project = e.getData(PlatformDataKeys.PROJECT)!!
        gerritUtil.saveDraftComment(
            changeInfo._number, revisionId, comment, project
        ) { commentInfo: CommentInfo ->
            commentsDiffTool.addComment(
                editor, changeInfo, revisionId, project, commentInfo
            )
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = gerritSettings.isLoginAndPasswordAvailable
    }
}
