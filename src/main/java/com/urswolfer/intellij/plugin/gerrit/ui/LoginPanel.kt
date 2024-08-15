/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.urswolfer.intellij.plugin.gerrit.ui

import com.intellij.ui.*
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubLoginPanel
 *
 * @author oleg
 * @author Urs Wolfer
 */
class LoginPanel(dialog: LoginDialog) {
    private lateinit var pane: JPanel
    private lateinit var hostTextField: JBTextField
    private lateinit var loginTextField: JTextField
    private lateinit var passwordField: JPasswordField
    private lateinit var gerritLoginInfoTestField: JTextPane

    init {
        hostTextField.emptyText.setText("https://review.example.org")

        hostTextField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                SettingsPanel.fixUrl(hostTextField)
            }
        })
        val listener: DocumentListener = object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                dialog.clearErrors()
            }
        }
        loginTextField.document.addDocumentListener(listener)
        passwordField.document.addDocumentListener(listener)
        gerritLoginInfoTestField.text = LOGIN_CREDENTIALS_INFO
        gerritLoginInfoTestField.margin = Insets(5, 0, 0, 0)
        gerritLoginInfoTestField.background = UIUtil.TRANSPARENT_COLOR
    }

    val panel: JComponent
        get() = pane

    var host: String?
        get() = hostTextField.text.trim { it <= ' ' }
        set(host) {
            hostTextField.text = host
        }

    var login: String?
        get() = loginTextField.text.trim { it <= ' ' }
        set(login) {
            loginTextField.text = login
        }

    var password: String?
        get() = String(passwordField.password)
        set(password) {
            passwordField.text = password
        }

    val preferrableFocusComponent: JComponent
        get() = if (hostTextField.text.isEmpty()) hostTextField else loginTextField

    companion object {
        const val LOGIN_CREDENTIALS_INFO: String =
            "* For the best experience, it is suggested that you set a HTTP access password" +
                    " for your account in the Gerrit Web Application (Settings > HTTP Password)." +
                    " If this does not work, you can also try to use your usual Gerrit credentials."
    }
}

