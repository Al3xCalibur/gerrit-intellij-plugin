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
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.EditorTextField
import com.urswolfer.intellij.plugin.gerrit.ui.SafeHtmlTextEditor
import com.urswolfer.intellij.plugin.gerrit.util.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import kotlin.String

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
class CommentForm(
    project: Project?,
    private val editor: Editor,
    private val filePath: String,
    private val commentSide: Side?,
    private val commentToEdit: Comment?
) : JPanel(BorderLayout()) {
    private val resolvedCheckBox: JCheckBox

    private val reviewTextField: EditorTextField
    private lateinit var balloon: JBPopup
    var comment: DraftInput? = null
        private set

    init {
        val safeHtmlTextEditor = SafeHtmlTextEditor(project)
        reviewTextField = safeHtmlTextEditor.messageField
        add(safeHtmlTextEditor)

        resolvedCheckBox = JCheckBox("Resolved")

        addButtons()

        reviewTextField.preferredSize = Dimension(BALLOON_WIDTH, BALLOON_HEIGHT)

        reviewTextField.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeymapUtil.getKeyStroke(CommonShortcuts.CTRL_ENTER), "postComment")
        reviewTextField.actionMap.put("postComment", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                createCommentAndClose()
            }
        })

        if (commentToEdit != null) {
            reviewTextField.text = commentToEdit.message
            resolvedCheckBox.isSelected = true != commentToEdit.unresolved
        }
    }

    private fun addButtons() {
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

        val saveButton = JButton("Save")
        buttonPanel.add(saveButton)
        saveButton.addActionListener { createCommentAndClose() }

        buttonPanel.add(resolvedCheckBox)

        buttonPanel.add(Box.createHorizontalGlue())

        val cancelButton = JButton("Cancel")
        buttonPanel.add(cancelButton)
        cancelButton.addActionListener { balloon.cancel() }

        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun createCommentAndClose() {
        comment = createComment()
        balloon.dispose()
    }

    private fun createComment(): DraftInput {
        val comment = DraftInput()

        comment.message = text
        comment.path = PathUtils.ensureSlashSeparators(filePath)
        comment.side = commentSide
        comment.unresolved = !resolvedCheckBox.isSelected

        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            comment.range = handleRangeComment(selectionModel)
            comment.line = comment.range.endLine // end line as per specification
        } else {
            comment.line = editor.document.getLineNumber(editor.caretModel.offset) + 1
        }

        if (commentToEdit != null) { // preserve: the selection might not exist anymore but we should not loose it
            comment.range = commentToEdit.range
            comment.line = commentToEdit.line
            comment.inReplyTo = commentToEdit.inReplyTo
        }

        return comment
    }

    override fun requestFocus() {
        IdeFocusManager.findInstanceByComponent(reviewTextField).requestFocus(reviewTextField, true)
    }

    val text: String
        get() = reviewTextField.text

    fun setBalloon(balloon: JBPopup) {
        this.balloon = balloon
    }

    private fun handleRangeComment(selectionModel: SelectionModel): Comment.Range {
        val startSelection = selectionModel.blockSelectionStarts[0]
        val endSelection = selectionModel.blockSelectionEnds[0]
        val charsSequence = editor.markupModel.document.charsSequence
        return RangeUtils.textOffsetToRange(charsSequence, startSelection, endSelection)
    }

    companion object {
        private const val BALLOON_WIDTH = 550
        private const val BALLOON_HEIGHT = 300
    }
}
