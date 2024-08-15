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

import com.google.gerrit.extensions.client.Comment
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.CommentInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.text.DateFormatUtil
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.util.CommentHelper
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * @author Urs Wolfer
 */
class CommentGutterIconRenderer(
    private val commentsDiffTool: CommentsDiffTool,
    private val editor: Editor,
    private val gerritUtil: GerritUtil,
    private val gerritSettings: GerritSettings,
    private val addCommentActionBuilder: AddCommentActionBuilder,
    private val fileComment: Comment,
    private val changeInfo: ChangeInfo,
    private val revisionId: String?,
    private val lineHighlighter: RangeHighlighter,
    private val rangeHighlighter: RangeHighlighter?
) : GutterIconRenderer() {
    override fun getIcon(): Icon {
        return if (isNewCommentFromMyself) {
            AllIcons.Toolwindows.ToolWindowTodo
        } else {
            AllIcons.Toolwindows.ToolWindowMessages
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        val that = o as CommentGutterIconRenderer

        return CommentHelper.equals(fileComment, that.fileComment)
    }

    override fun hashCode(): Int {
        return CommentHelper.hashCode(fileComment)
    }

    override fun getTooltipText(): String {
        return "<strong>$authorName</strong> (${
            if (fileComment.updated != null) DateFormatUtil.formatPrettyDateTime(
                fileComment.updated
            ) else "draft"
        })<br/>${TextToHtml.textToHtml(fileComment.message)}"
    }

    override fun getPopupMenuActions(): ActionGroup {
        return createPopupMenuActionGroup()
    }

    private val isNewCommentFromMyself: Boolean
        get() = fileComment is CommentInfo && (fileComment.author == null)

    override fun getClickAction(): AnAction {
        return object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
                val inputEvent = e.inputEvent as MouseEvent
                val actionManager = ActionManager.getInstance()
                val actionGroup = createPopupMenuActionGroup()
                val popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup)
                popupMenu.component.show(inputEvent.component, inputEvent.x, inputEvent.y)
            }
        }
    }

    private fun createPopupMenuActionGroup(): DefaultActionGroup {
        val actionGroup = DefaultActionGroup()
        if (isNewCommentFromMyself) {
            val commentAction = addCommentActionBuilder.create(
                commentsDiffTool, changeInfo, revisionId, editor, fileComment.path, fileComment.side,
                text = "Edit", icon = AllIcons.Toolwindows.ToolWindowMessages,
                commentToEdit = fileComment, lineHighlighter = lineHighlighter, rangeHighlighter = rangeHighlighter
            )
            actionGroup.add(commentAction)

            val removeCommentAction = RemoveCommentAction(
                commentsDiffTool, editor, gerritUtil, changeInfo, fileComment, revisionId,
                lineHighlighter, rangeHighlighter
            )
            actionGroup.add(removeCommentAction)
        } else {
            val commentAction = addCommentActionBuilder.create(
                commentsDiffTool, changeInfo, revisionId, editor, fileComment.path, fileComment.side,
                text = "Reply", icon = AllIcons.Actions.Back, replyToComment = fileComment
            )
            actionGroup.add(commentAction)

            val commentDoneAction = CommentDoneAction(
                editor, commentsDiffTool, gerritUtil, gerritSettings, fileComment, changeInfo, revisionId
            )
            actionGroup.add(commentDoneAction)
        }
        return actionGroup
    }

    private val authorName: String
        get() {
            var name = "Myself"
            if (!isNewCommentFromMyself) {
                val author = (fileComment as CommentInfo).author
                if (author != null) {
                    name = author.name
                }
            }
            return name
        }
}
