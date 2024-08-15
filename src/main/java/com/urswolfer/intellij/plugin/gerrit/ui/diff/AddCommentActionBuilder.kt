package com.urswolfer.intellij.plugin.gerrit.ui.diff

import com.google.gerrit.extensions.client.Comment
import com.google.gerrit.extensions.client.Side
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.inject.Inject
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import javax.swing.Icon

/**
 * @author Thomas Forrer
 */
class AddCommentActionBuilder @Inject constructor(
    private val commentBalloonBuilder: CommentBalloonBuilder,
    private val gerritUtil: GerritUtil,
    private val gerritSettings: GerritSettings
) {
    fun create(
        commentsDiffTool: CommentsDiffTool,
        changeInfo: ChangeInfo?,
        revisionId: String?,
        editor: Editor,
        filePath: String,
        commentSide: Side
    ): Builder {
        return Builder().init(commentsDiffTool, changeInfo, revisionId, editor, filePath, commentSide)
    }

    inner class Builder {
        private var text: String? = null
        private var icon: Icon? = null
        private var commentsDiffTool: CommentsDiffTool? = null
        private var changeInfo: ChangeInfo? = null
        private var revisionId: String? = null
        private var editor: Editor? = null
        private var filePath: String? = null
        private var commentSide: Side? = null
        private var commentToEdit: Comment? = null
        private var lineHighlighter: RangeHighlighter? = null
        private var rangeHighlighter: RangeHighlighter? = null
        private var replyToComment: Comment? = null

        fun init(
            commentsDiffTool: CommentsDiffTool,
            changeInfo: ChangeInfo?,
            revisionId: String?,
            editor: Editor,
            filePath: String,
            commentSide: Side
        ): Builder {
            this.commentsDiffTool = commentsDiffTool
            this.changeInfo = changeInfo
            this.revisionId = revisionId
            this.editor = editor
            this.filePath = filePath
            this.commentSide = commentSide
            return this
        }

        fun withText(text: String?): Builder {
            this.text = text
            return this
        }

        fun withIcon(icon: Icon?): Builder {
            this.icon = icon
            return this
        }

        fun update(
            commentToEdit: Comment?,
            lineHighlighter: RangeHighlighter?,
            rangeHighlighter: RangeHighlighter?
        ): Builder {
            this.commentToEdit = commentToEdit
            this.lineHighlighter = lineHighlighter
            this.rangeHighlighter = rangeHighlighter
            return this
        }

        fun reply(replyToComment: Comment?): Builder {
            this.replyToComment = replyToComment
            return this
        }

        fun get(): AddCommentAction {
            return AddCommentAction(
                text,
                icon,
                commentsDiffTool,
                gerritUtil,
                gerritSettings,
                editor,
                commentBalloonBuilder,
                changeInfo,
                revisionId,
                filePath,
                commentSide,
                commentToEdit,
                lineHighlighter,
                rangeHighlighter,
                replyToComment
            )
        }
    }
}
