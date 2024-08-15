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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import javax.swing.Action
import javax.swing.JComponent

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubLoginDialog
 *
 * @author oleg
 * @author Urs Wolfer
 */
class LoginDialog(
    private val project: Project?,
    private val gerritSettings: GerritSettings?,
    private val gerritUtil: GerritUtil?,
    private val log: Logger?
) : DialogWrapper(
    project, true
) {
    private val loginPanel = LoginPanel(this)

    // TODO: login must be merged with tasks server settings
    init {
        loginPanel.host = gerritSettings!!.host
        loginPanel.login = gerritSettings.login
        loginPanel.password = gerritSettings.password
        title = "Login to Gerrit"
        setOKButtonText("Login")
        init()
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction, helpAction)
    }

    override fun createCenterPanel(): JComponent {
        return loginPanel.panel
    }

    override fun getHelpId(): String {
        return "login_to_gerrit"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return loginPanel.preferrableFocusComponent
    }

    override fun doOKAction() {
        val login = loginPanel.login
        val password = loginPanel.password
        val host = loginPanel.host
        val gerritAuthData = GerritAuthData.Basic(host, login, password)
        try {
            val loggedSuccessfully = gerritUtil!!.checkCredentials(project, gerritAuthData)
            if (loggedSuccessfully) {
                gerritSettings!!.setLogin(login)
                gerritSettings.setPassword(password)
                gerritSettings.host = host!!
                super.doOKAction()
            } else {
                setErrorText("Can't login with given credentials")
            }
        } catch (e: Exception) {
            log!!.info(e)
            setErrorText("Can't login: " + gerritUtil!!.getErrorTextFromException(e))
        }
    }

    fun clearErrors() {
        setErrorText(null)
    }
}
