/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.google.common.base.Strings
import com.google.inject.Inject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBTextField
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsPanel
 *
 * @author oleg
 * @author Urs Wolfer
 */
class SettingsPanel {
    @Inject
    private lateinit var gerritSettings: GerritSettings
    @Inject
    private lateinit var gerritUtil: GerritUtil
    @Inject
    private lateinit var log: Logger

    private lateinit var loginTextField: JTextField
    private lateinit var passwordField: JPasswordField
    private lateinit var gerritLoginInfoTextField: JTextPane
    private lateinit var loginPane: JPanel
    private lateinit var testButton: JButton
    private lateinit var hostTextField: JBTextField
    private lateinit var refreshTimeoutSpinner: JSpinner
    private lateinit var settingsPane: JPanel
    private lateinit var pane: JPanel
    private lateinit var notificationOnNewReviewsCheckbox: JCheckBox
    private lateinit var automaticRefreshCheckbox: JCheckBox
    private lateinit var listAllChangesCheckbox: JCheckBox
    private lateinit var pushToGerritCheckbox: JCheckBox
    private lateinit var showChangeNumberColumnCheckBox: JCheckBox
    private lateinit var showChangeIdColumnCheckBox: JCheckBox
    private lateinit var showTopicColumnCheckBox: JCheckBox
    private lateinit var showProjectColumnComboBox: JComboBox<ShowProjectColumn>
    private lateinit var cloneBaseUrlTextField: JTextField
    private lateinit var forceDefaultBranchCheckBox: JCheckBox

    var isPasswordModified: Boolean = false
        private set

    init {
        hostTextField.emptyText.setText("https://review.example.org")

        gerritLoginInfoTextField.text = LoginPanel.LOGIN_CREDENTIALS_INFO
        gerritLoginInfoTextField.background = pane.background
        testButton.addActionListener { e: ActionEvent? ->
            val password = if (isPasswordModified) password else gerritSettings.password
            if (Strings.isNullOrEmpty(host)) {
                Messages.showErrorDialog(pane, "Required field URL not specified", "Test Failure")
                return@addActionListener
            }
            try {
                val gerritAuthData: GerritAuthData.Basic = object : GerritAuthData.Basic(host, login, password) {
                    override fun isLoginAndPasswordAvailable(): Boolean {
                        return !login.isNullOrEmpty()
                    }
                }
                if (gerritUtil.checkCredentials(ProjectManager.getInstance().defaultProject, gerritAuthData)) {
                    Messages.showInfoMessage(pane, "Connection successful", "Success")
                } else {
                    Messages.showErrorDialog(pane, "Can't login to $host using given credentials", "Login Failure")
                }
            } catch (ex: Exception) {
                log.info(ex)
                Messages.showErrorDialog(pane, "Can't login to $host: ${gerritUtil.getErrorTextFromException(ex)}",
                    "Login Failure")
            }
            this.password = password
        }

        hostTextField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                fixUrl(hostTextField)
            }
        })

        passwordField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                this@SettingsPanel.isPasswordModified = true
            }

            override fun removeUpdate(e: DocumentEvent) {
                this@SettingsPanel.isPasswordModified = true
            }

            override fun changedUpdate(e: DocumentEvent) {
                this@SettingsPanel.isPasswordModified = true
            }
        })

        automaticRefreshCheckbox.addActionListener { e: ActionEvent? -> updateAutomaticRefresh() }

        showProjectColumnComboBox.setModel(EnumComboBoxModel(ShowProjectColumn::class.java))

        cloneBaseUrlTextField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                fixUrl(cloneBaseUrlTextField)
            }
        })
    }

    private fun updateAutomaticRefresh() {
        GuiUtils.enableChildren(refreshTimeoutSpinner, automaticRefreshCheckbox.isSelected)
    }

    val panel: JComponent
        get() = pane

    var login: String?
        get() = loginTextField.text.trim { it <= ' ' }
        set(login) {
            loginTextField.text = login
        }

    var password: String?
        get() = String(passwordField.password)
        set(password) {
            // Show password as blank if password is empty
            passwordField.text = if (StringUtil.isEmpty(password)) null else password
        }

    var host: String
        get() = hostTextField.text.trim { it <= ' ' }
        set(host) {
            hostTextField.text = host
        }

    var listAllChanges: Boolean
        get() = listAllChangesCheckbox.isSelected
        set(listAllChanges) {
            listAllChangesCheckbox.isSelected = listAllChanges
        }

    var automaticRefresh: Boolean
        get() = automaticRefreshCheckbox.isSelected
        set(automaticRefresh) {
            automaticRefreshCheckbox.isSelected = automaticRefresh
            updateAutomaticRefresh()
        }

    var refreshTimeout: Int
        get() = refreshTimeoutSpinner.value as Int
        set(refreshTimeout) {
            refreshTimeoutSpinner.value = refreshTimeout
        }

    var reviewNotifications: Boolean
        get() = notificationOnNewReviewsCheckbox.isSelected
        set(reviewNotifications) {
            notificationOnNewReviewsCheckbox.isSelected = reviewNotifications
        }

    var pushToGerrit: Boolean
        get() = pushToGerritCheckbox.isSelected
        set(pushToGerrit) {
            pushToGerritCheckbox.isSelected = pushToGerrit
        }

    var showChangeNumberColumn: Boolean
        get() = showChangeNumberColumnCheckBox.isSelected
        set(showChangeNumberColumn) {
            showChangeNumberColumnCheckBox.isSelected = showChangeNumberColumn
        }

    var showChangeIdColumn: Boolean
        get() = showChangeIdColumnCheckBox.isSelected
        set(showChangeIdColumn) {
            showChangeIdColumnCheckBox.isSelected = showChangeIdColumn
        }

    var showTopicColumn: Boolean
        get() = showTopicColumnCheckBox.isSelected
        set(showTopicColumn) {
            showTopicColumnCheckBox.isSelected = showTopicColumn
        }

    var showProjectColumn: ShowProjectColumn?
        get() = showProjectColumnComboBox.model.selectedItem as ShowProjectColumn
        set(showProjectColumn) {
            showProjectColumnComboBox.model.selectedItem = showProjectColumn
        }

    fun resetPasswordModification() {
        isPasswordModified = false
    }

    var cloneBaseUrl: String
        get() = cloneBaseUrlTextField.text.trim { it <= ' ' }
        set(cloneBaseUrl) {
            cloneBaseUrlTextField.text = cloneBaseUrl
        }

    var forceDefaultBranch: Boolean
        get() = forceDefaultBranchCheckBox.isSelected
        set(forceDefaultBranch) {
            forceDefaultBranchCheckBox.isSelected = forceDefaultBranch
        }

    companion object {
        fun fixUrl(textField: JTextField) {
            var text = textField.text
            if (text.endsWith("/")) {
                text = text.substring(0, text.length - 1)
            }
            if (text.isNotEmpty() && !text.contains("://")) {
                text = "http://$text"
            }
            textField.text = text
        }
    }
}

