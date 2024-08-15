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
        changeInfo: ChangeInfo,
        revisionId: String?,
        editor: Editor,
        filePath: String,
        commentSide: Side,
        text: String,
        icon: Icon,
        commentToEdit: Comment? = null,
        lineHighlighter: RangeHighlighter? = null,
        rangeHighlighter: RangeHighlighter? = null,
        replyToComment: Comment? = null
    ): AddCommentAction {
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
