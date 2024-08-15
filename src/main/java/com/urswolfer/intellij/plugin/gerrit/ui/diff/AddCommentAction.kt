/*
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
package com.urswolfer.intellij.plugin.gerrit.ui.diff

import com.google.gerrit.extensions.api.changes.DraftInput
import com.google.gerrit.extensions.client.Comment
import com.google.gerrit.extensions.client.Side
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.CommentInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import javax.swing.Icon

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
// added with code
class AddCommentAction(
    label: String?,
    icon: Icon?,
    private val commentsDiffTool: CommentsDiffTool,
    private val gerritUtil: GerritUtil,
    private val gerritSettings: GerritSettings,
    private val editor: Editor,
    private val commentBalloonBuilder: CommentBalloonBuilder,
    private val changeInfo: ChangeInfo,
    private val revisionId: String?,
    private val filePath: String?,
    private val commentSide: Side?,
    private val commentToEdit: Comment?,
    private val lineHighlighter: RangeHighlighter?,
    private val rangeHighlighter: RangeHighlighter?,
    private val replyToComment: Comment?
) : AnAction(label, null, icon), DumbAware, UpdateInBackground {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return
        addVersionedComment(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = gerritSettings.isLoginAndPasswordAvailable
    }

    private fun addVersionedComment(project: Project) {
        if (filePath == null) return

        val commentForm = CommentForm(project, editor, filePath, commentSide, commentToEdit)
        val balloon = commentBalloonBuilder.getNewCommentBalloon(commentForm, "Comment")
        balloon.addListener(object : JBPopupListener {
            override fun beforeShown(lightweightWindowEvent: LightweightWindowEvent) {}

            override fun onClosed(event: LightweightWindowEvent) {
                val comment = commentForm.comment
                if (comment != null) {
                    handleComment(comment, project)
                }
            }
        })
        commentForm.setBalloon(balloon)
        balloon.showInBestPositionFor(editor)
        commentForm.requestFocus()
    }

    private fun handleComment(comment: DraftInput, project: Project) {
        if (commentToEdit != null) {
            comment.id = commentToEdit.id
        }

        if (replyToComment != null) {
            comment.inReplyTo = replyToComment.id
            comment.side = replyToComment.side
            comment.line = replyToComment.line
            comment.range = replyToComment.range
        }

        gerritUtil.saveDraftComment(
            changeInfo._number, revisionId, comment, project
        ) { commentInfo: CommentInfo ->
            if (commentToEdit != null) {
                commentsDiffTool.removeComment(project, editor, lineHighlighter, rangeHighlighter)
            }
            commentsDiffTool.addComment(editor, changeInfo, revisionId, project, commentInfo)
        }
    }
}
