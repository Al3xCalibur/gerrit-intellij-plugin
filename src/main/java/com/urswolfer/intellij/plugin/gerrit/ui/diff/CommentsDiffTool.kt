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

import com.google.common.collect.Lists
import com.google.common.collect.Ordering
import com.google.common.primitives.Longs
import com.google.gerrit.extensions.client.Comment
import com.google.gerrit.extensions.client.Side
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.CommentInfo
import com.google.gerrit.extensions.common.RevisionInfo
import com.google.inject.Inject
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffTool
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.SuppressiveDiffTool
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.util.GerritUserDataKeys
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
open class CommentsDiffTool : FrameDiffTool, SuppressiveDiffTool {
    @Inject
    private lateinit var gerritUtil: GerritUtil

    @Inject
    private lateinit var gerritSettings: GerritSettings

    @Inject
    private lateinit var addCommentActionBuilder: AddCommentActionBuilder

    @Inject
    private lateinit var pathUtils: PathUtils

    @Inject
    private lateinit var selectedRevisions: SelectedRevisions

    override fun getName(): String {
        return SimpleDiffTool.INSTANCE.name
    }

    override fun getSuppressedTools(): List<Class<out DiffTool>> {
        return listOf(
            UnifiedDiffTool.INSTANCE.javaClass,
            SimpleDiffTool.INSTANCE.javaClass
        )
    }

    override fun canShow(context: DiffContext, request: DiffRequest): Boolean {
        if (context.getUserData<ChangeInfo?>(GerritUserDataKeys.CHANGE) == null) return false
        if (context.getUserData<Pair<String, RevisionInfo>?>(GerritUserDataKeys.BASE_REVISION) == null) return false
        if (request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) == null) return false
        return (SimpleDiffViewer.canShowRequest(context, request)
                || SimpleOnesideDiffViewer.canShowRequest(context, request))
    }

    override fun createComponent(context: DiffContext, request: DiffRequest): FrameDiffTool.DiffViewer {
        return if (SimpleDiffViewer.canShowRequest(context, request)) {
            SimpleCommentsDiffViewer(context, request)
        } else {
            SimpleOnesideCommentsDiffViewer(context, request)
        }
    }

    private fun handleComments(
        editor1: EditorEx?,
        editor2: EditorEx,
        change: Change,
        project: Project,
        changeInfo: ChangeInfo,
        selectedRevisionId: String?,
        baseRevision: Pair<String, RevisionInfo>?
    ) {
        val filePath = ChangesUtil.getFilePath(change)
        val relativeFilePath: String =
            PathUtils.ensureSlashSeparators(getRelativeOrAbsolutePath(project, filePath.path, changeInfo))

        addCommentAction(editor1, editor2, relativeFilePath, changeInfo, selectedRevisionId, baseRevision)

        gerritUtil.getComments(
            changeInfo._number, selectedRevisionId, project, includePublishedComments = true, includeDraftComments = true
        ) { comments ->
            val fileComments = comments[relativeFilePath]
            if (fileComments != null) {
                addCommentsGutter(
                    editor2,
                    relativeFilePath,
                    selectedRevisionId,
                    fileComments.filter(REVISION_COMMENT),
                    changeInfo,
                    project
                )
                if (baseRevision == null) {
                    addCommentsGutter(
                        editor1,
                        relativeFilePath,
                        selectedRevisionId,
                        fileComments.filterNot(REVISION_COMMENT),
                        changeInfo,
                        project
                    )
                }
            }
        }

        if (baseRevision != null) {
            gerritUtil.getComments(
                changeInfo._number, baseRevision.first, project, includePublishedComments = true, includeDraftComments = true
            ) { comments ->
                val fileComments = comments[relativeFilePath]
                if (fileComments != null) {
                    addCommentsGutter(
                        editor1,
                        relativeFilePath,
                        baseRevision.first,
                        fileComments.sortedWith(COMMENT_ORDERING).filter(REVISION_COMMENT),
                        changeInfo,
                        project
                    )
                }
            }
        }

        gerritUtil.setReviewed(
            changeInfo._number, selectedRevisionId,
            relativeFilePath, project
        )
    }

    private fun addCommentAction(
        editor1: EditorEx?, editor2: EditorEx, filePath: String, changeInfo: ChangeInfo,
        selectedRevisionId: String?, baseRevision: Pair<String, RevisionInfo>?
    ) {
        if (baseRevision != null) {
            addCommentActionToEditor(editor1, filePath, changeInfo, baseRevision.first, Side.REVISION)
        } else {
            addCommentActionToEditor(editor1, filePath, changeInfo, selectedRevisionId, Side.PARENT)
        }
        addCommentActionToEditor(editor2, filePath, changeInfo, selectedRevisionId, Side.REVISION)
    }

    private fun addCommentActionToEditor(
        editor: Editor?,
        filePath: String,
        changeInfo: ChangeInfo,
        revisionId: String?,
        commentSide: Side
    ) {
        if (editor == null) return

        val group = DefaultActionGroup()
        val addCommentAction = addCommentActionBuilder.create(
            this, changeInfo, revisionId, editor, filePath, commentSide,
            text = "Add Comment", icon = AllIcons.Toolwindows.ToolWindowMessages
        )
        addCommentAction.registerCustomShortcutSet(CustomShortcutSet.fromString("C"), editor.contentComponent)
        group.add(addCommentAction)
        PopupHandler.installPopupHandler(editor.contentComponent, group, "GerritCommentDiffPopup")
    }

    private fun addCommentsGutter(
        editor: Editor?,
        filePath: String,
        revisionId: String?,
        fileComments: Iterable<CommentInfo>,
        changeInfo: ChangeInfo,
        project: Project?
    ) {
        for (fileComment in fileComments) {
            fileComment.path = PathUtils.ensureSlashSeparators(filePath)
            addComment(editor, changeInfo, revisionId, project, fileComment)
        }
    }

    open fun addComment(
        editor: Editor?,
        changeInfo: ChangeInfo,
        revisionId: String?,
        project: Project?,
        comment: Comment
    ) {
        if (editor == null) return
        val markup = editor.markupModel

        var rangeHighlighter: RangeHighlighter? = null
        if (comment.range != null) {
            rangeHighlighter = highlightRangeComment(comment.range, editor, project)
        }

        val lineCount = markup.document.lineCount

        var line = (if (comment.line != null) comment.line else 0) - 1
        if (line < 0) {
            line = 0
        }
        if (line > lineCount - 1) {
            line = lineCount - 1
        }
        if (line >= 0) {
            val highlighter = markup.addLineHighlighter(line, HighlighterLayer.ERROR + 1, null)
            val iconRenderer = CommentGutterIconRenderer(
                this, editor, gerritUtil, gerritSettings, addCommentActionBuilder,
                comment, changeInfo, revisionId, highlighter, rangeHighlighter
            )
            highlighter.gutterIconRenderer = iconRenderer
        }
    }

    open fun removeComment(
        project: Project?,
        editor: Editor,
        lineHighlighter: RangeHighlighter?,
        rangeHighlighter: RangeHighlighter?
    ) {
        editor.markupModel.removeHighlighter(lineHighlighter!!)
        lineHighlighter.dispose()

        if (rangeHighlighter != null) {
            val highlightManager = HighlightManager.getInstance(project)
            highlightManager.removeSegmentHighlighter(editor, rangeHighlighter)
        }
    }

    private fun handleDiffViewer(
        diffContext: DiffContext, diffRequest: ContentDiffRequest,
        editor1: EditorEx?, editor2: EditorEx
    ) {
        val changeInfo = diffContext.getUserData<ChangeInfo?>(GerritUserDataKeys.CHANGE)!!
        val baseRevision =
            diffContext.getUserData<Pair<String, RevisionInfo>?>(GerritUserDataKeys.BASE_REVISION)
        val selectedRevisionId = selectedRevisions[changeInfo]
        val change = diffRequest.getUserData(ChangeDiffRequestProducer.CHANGE_KEY)!!
        handleComments(editor1, editor2, change, diffContext.project!!, changeInfo, selectedRevisionId, baseRevision)
    }

    private inner class SimpleCommentsDiffViewer(context: DiffContext, request: DiffRequest) :
        SimpleDiffViewer(context, request) {
        override fun onInit() {
            super.onInit()
            handleDiffViewer(myContext, myRequest, editor1, editor2)
        }
    }

    private inner class SimpleOnesideCommentsDiffViewer(context: DiffContext, request: DiffRequest) :
        SimpleOnesideDiffViewer(context, request) {
        override fun onInit() {
            super.onInit()
            handleDiffViewer(myContext, myRequest, null, editor)
        }
    }

    private fun getRelativeOrAbsolutePath(
        project: Project,
        absoluteFilePath: String,
        changeInfo: ChangeInfo
    ): String {
        return pathUtils.getRelativeOrAbsolutePath(project, absoluteFilePath, changeInfo.project)
    }

    class Proxy : CommentsDiffTool() {
        private val delegate: CommentsDiffTool = GerritModule.getInstance<CommentsDiffTool>()

        override fun getName(): String {
            return delegate.name
        }

        override fun getSuppressedTools(): List<Class<out DiffTool>> {
            return delegate.suppressedTools
        }

        override fun canShow(context: DiffContext, request: DiffRequest): Boolean {
            return delegate.canShow(context, request)
        }

        override fun createComponent(context: DiffContext, request: DiffRequest): FrameDiffTool.DiffViewer {
            return delegate.createComponent(context, request)
        }

        override fun addComment(
            editor: Editor?,
            changeInfo: ChangeInfo,
            revisionId: String?,
            project: Project?,
            comment: Comment
        ) {
            delegate.addComment(editor, changeInfo, revisionId, project, comment)
        }

        override fun removeComment(
            project: Project?,
            editor: Editor,
            lineHighlighter: RangeHighlighter?,
            rangeHighlighter: RangeHighlighter?
        ) {
            delegate.removeComment(project, editor, lineHighlighter, rangeHighlighter)
        }
    }

    companion object {
        private val REVISION_COMMENT = { comment: Comment ->
            comment.side == null || comment.side == Side.REVISION
        }

        private val COMMENT_ORDERING: Ordering<Comment> = object : Ordering<Comment>() {
            override fun compare(left: Comment?, right: Comment?): Int {
                // need to sort descending as icons are added to the left of existing icons
                return -Longs.compare(left!!.updated.time, right!!.updated.time)
            }
        }

        private fun highlightRangeComment(range: Comment.Range, editor: Editor, project: Project?): RangeHighlighter {
            val charsSequence = editor.markupModel.document.charsSequence

            val offset = RangeUtils.rangeToTextOffset(charsSequence, range)

            val attributes = TextAttributes()
            attributes.backgroundColor = JBColor.YELLOW
            val highlighters = Lists.newArrayList<RangeHighlighter>()
            val highlightManager = HighlightManager.getInstance(project)
            highlightManager.addRangeHighlight(editor, offset.start, offset.end, attributes, false, highlighters)
            return highlighters[0]
        }
    }
}
