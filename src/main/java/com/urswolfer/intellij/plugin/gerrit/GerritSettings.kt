/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.urswolfer.intellij.plugin.gerrit

import com.google.common.base.Strings
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.intellij.plugin.gerrit.ui.ShowProjectColumn
import org.jdom.Element

/**
 * Parts based on org.jetbrains.plugins.github.GithubSettings
 *
 * @author oleg
 * @author Urs Wolfer
 */
@State(name = "GerritSettings", storages = [Storage("gerrit_settings.xml")])
class GerritSettings : PersistentStateComponent<Element>, GerritAuthData {
    private var login = ""
    private var host = ""
    var listAllChanges: Boolean = false
    var automaticRefresh: Boolean = true
    var refreshTimeout: Int = 15
    var reviewNotifications: Boolean = true
    var pushToGerrit: Boolean = false
    var showChangeNumberColumn: Boolean = false
    var showChangeIdColumn: Boolean = false
    var showTopicColumn: Boolean = false
    var showProjectColumn: ShowProjectColumn? = ShowProjectColumn.AUTO
    var cloneBaseUrl: String = ""
    var forceDefaultBranch: Boolean = false

    private var preloadedPassword: String? = null

    private lateinit var log: Logger

    override fun getState(): Element {
        val element = Element(GERRIT_SETTINGS_TAG)
        element.setAttribute(LOGIN, getLogin())
        element.setAttribute(HOST, getHost())
        element.setAttribute(LIST_ALL_CHANGES, listAllChanges.toString())
        element.setAttribute(AUTOMATIC_REFRESH, automaticRefresh.toString())
        element.setAttribute(REFRESH_TIMEOUT, refreshTimeout.toString())
        element.setAttribute(REVIEW_NOTIFICATIONS, reviewNotifications.toString())
        element.setAttribute(PUSH_TO_GERRIT, pushToGerrit.toString())
        element.setAttribute(SHOW_CHANGE_NUMBER_COLUMN, showChangeNumberColumn.toString())
        element.setAttribute(SHOW_CHANGE_ID_COLUMN, showChangeIdColumn.toString())
        element.setAttribute(SHOW_TOPIC_COLUMN, showTopicColumn.toString())
        element.setAttribute(SHOW_PROJECT_COLUMN, showProjectColumn!!.name)
        element.setAttribute(CLONE_BASE_URL, cloneBaseUrl)
        element.setAttribute(FORCE_DEFAULT_BRANCH, forceDefaultBranch.toString())
        return element
    }

    override fun loadState(element: Element) {
        // All the logic on retrieving password was moved to getPassword action to cleanup initialization process
        try {
            setLogin(element.getAttributeValue(LOGIN))
            setHost(element.getAttributeValue(HOST))

            listAllChanges = getBooleanValue(element, LIST_ALL_CHANGES)
            automaticRefresh = getBooleanValue(element, AUTOMATIC_REFRESH)
            refreshTimeout = getIntegerValue(element, REFRESH_TIMEOUT)
            reviewNotifications = getBooleanValue(element, REVIEW_NOTIFICATIONS)
            pushToGerrit = getBooleanValue(element, PUSH_TO_GERRIT)
            showChangeNumberColumn = getBooleanValue(element, SHOW_CHANGE_NUMBER_COLUMN)
            showChangeIdColumn = getBooleanValue(element, SHOW_CHANGE_ID_COLUMN)
            showTopicColumn = getBooleanValue(element, SHOW_TOPIC_COLUMN)
            showProjectColumn = getShowProjectColumnValue(element, SHOW_PROJECT_COLUMN)
            cloneBaseUrl = element.getAttributeValue(CLONE_BASE_URL)
            forceDefaultBranch = getBooleanValue(element, FORCE_DEFAULT_BRANCH)
        } catch (e: Exception) {
            log.error("Error happened while loading gerrit settings: $e")
        }
    }

    private fun getBooleanValue(element: Element, attributeName: String): Boolean {
        val attributeValue = element.getAttributeValue(attributeName)
        return attributeValue?.toBoolean() ?: false
    }

    private fun getIntegerValue(element: Element, attributeName: String): Int {
        val attributeValue = element.getAttributeValue(attributeName)
        return attributeValue?.toInt() ?: 0
    }

    private fun getShowProjectColumnValue(element: Element, attributeName: String): ShowProjectColumn {
        val attributeValue = element.getAttributeValue(attributeName)
        return if (attributeValue != null) {
            ShowProjectColumn.valueOf(attributeValue)
        } else {
            ShowProjectColumn.AUTO
        }
    }

    override fun getLogin(): String {
        return login
    }

    override fun getPassword(): String {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            check(preloadedPassword != null) { "Need to call #preloadPassword when password is required in background thread" }
        } else {
            preloadPassword()
        }
        return preloadedPassword ?: ""
    }

    fun preloadPassword() {
        val credentials = PasswordSafe.instance.get(CREDENTIAL_ATTRIBUTES)
        val password = credentials?.getPasswordAsString()
        preloadedPassword = password
    }

    override fun isHttpPassword(): Boolean {
        return false
    }

    override fun getHost(): String {
        return host
    }

    override fun isLoginAndPasswordAvailable(): Boolean {
        return !Strings.isNullOrEmpty(getLogin())
    }

    fun setLogin(login: String?) {
        this.login = login ?: ""
    }

    fun setPassword(password: String?) {
        PasswordSafe.instance.set(CREDENTIAL_ATTRIBUTES, Credentials(null, password ?: ""))
    }

    fun forgetPassword() {
        PasswordSafe.instance.set(CREDENTIAL_ATTRIBUTES, null)
    }

    fun setHost(host: String) {
        this.host = host
    }

    fun setLog(log: Logger) {
        this.log = log
    }

    val cloneBaseUrlOrHost: String
        get() = if (Strings.isNullOrEmpty(cloneBaseUrl)) host else cloneBaseUrl

    companion object {
        private const val GERRIT_SETTINGS_TAG = "GerritSettings"
        private const val LOGIN = "Login"
        private const val HOST = "Host"
        private const val AUTOMATIC_REFRESH = "AutomaticRefresh"
        private const val LIST_ALL_CHANGES = "ListAllChanges"
        private const val REFRESH_TIMEOUT = "RefreshTimeout"
        private const val REVIEW_NOTIFICATIONS = "ReviewNotifications"
        private const val PUSH_TO_GERRIT = "PushToGerrit"
        private const val SHOW_CHANGE_NUMBER_COLUMN = "ShowChangeNumberColumn"
        private const val SHOW_CHANGE_ID_COLUMN = "ShowChangeIdColumn"
        private const val SHOW_TOPIC_COLUMN = "ShowTopicColumn"
        private const val SHOW_PROJECT_COLUMN = "ShowProjectColumn"
        private const val CLONE_BASE_URL = "CloneBaseUrl"
        private const val FORCE_DEFAULT_BRANCH = "ForceDefaultBranch"
        private const val GERRIT_SETTINGS_PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY"
        private val CREDENTIAL_ATTRIBUTES =
            CredentialAttributes(GerritSettings::class.java.name, GERRIT_SETTINGS_PASSWORD_KEY)
    }
}
