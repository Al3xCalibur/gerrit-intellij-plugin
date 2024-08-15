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

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.EditorTextField
import com.intellij.ui.TabbedPaneImpl
import com.intellij.util.ui.UIUtil
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ChangeEvent

/**
 * @author Urs Wolfer
 */
class SafeHtmlTextEditor(project: Project?) : JPanel(BorderLayout()) {
    val messageField: EditorTextField

    init {
        val tabbedPane = TabbedPaneImpl(SwingConstants.TOP)
        tabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS)

        messageField = CommitMessage(project!!).editorField
        messageField.border = BorderFactory.createEmptyBorder()
        val messagePanel = JPanel(BorderLayout())
        messagePanel.add(messageField, BorderLayout.CENTER)
        val markdownLinkLabel = JLabel(
            "<html>Write your comment here. " +
                    "You can use a <a href=\"\"> simple markdown-like syntax</a>.</html>"
        )
        markdownLinkLabel.cursor = Cursor(Cursor.HAND_CURSOR)
        markdownLinkLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                BrowserUtil.browse("https://gerrit-review.googlesource.com/Documentation/user-review-ui.html#summary-comment")
            }
        })
        messagePanel.add(markdownLinkLabel, BorderLayout.SOUTH)
        tabbedPane.addTab("Write", AllIcons.Actions.Edit, messagePanel)

        val previewEditorPane = JEditorPane(UIUtil.HTML_MIME, "")
        previewEditorPane.isEditable = false
        tabbedPane.addTab("Preview", AllIcons.Actions.Preview, previewEditorPane)

        tabbedPane.addChangeListener { e: ChangeEvent ->
            if ((e.source as TabbedPaneImpl).selectedComponent === previewEditorPane) {
                val content = String.format(
                    "<html><head>%s</head><body>%s</body></html>",
                    UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()),
                    TextToHtml.textToHtml(messageField.text)
                )
                previewEditorPane.text = content
            }
        }

        add(tabbedPane, SwingConstants.CENTER)
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(600, 400)
    }
}
