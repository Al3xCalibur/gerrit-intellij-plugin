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

import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import java.awt.BorderLayout
import javax.swing.*

/**
 * @author Urs Wolfer
 */
class ReviewPanel(project: Project?) : JPanel(BorderLayout()) {
    private val messageField: EditorTextField
    private val notifyCheckBox: JCheckBox
    private val submitCheckBox: JCheckBox

    init {
        val editor = SafeHtmlTextEditor(project)
        messageField = editor.messageField
        add(editor, BorderLayout.CENTER)

        val southPanel = JPanel()
        val southLayout = BoxLayout(southPanel, BoxLayout.Y_AXIS)
        southPanel.layout = southLayout
        add(southPanel, BorderLayout.SOUTH)

        notifyCheckBox = JCheckBox("Send Notification Mails", true)
        southPanel.add(notifyCheckBox)

        submitCheckBox = JCheckBox("Submit Change")
        southPanel.add(submitCheckBox)

        border = BorderFactory.createEmptyBorder()
    }

    var message: String?
        get() = messageField.text.trim { it <= ' ' }
        set(message) {
            messageField.setText(message)
        }

    val submitChange: Boolean
        get() = submitCheckBox.isSelected

    val doNotify: Boolean
        get() = notifyCheckBox.isSelected

    val preferrableFocusComponent: JComponent
        get() = messageField
}

