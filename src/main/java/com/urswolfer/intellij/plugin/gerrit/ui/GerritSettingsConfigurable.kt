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

import com.google.inject.Inject
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsConfigurableProvider
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import javax.swing.JComponent

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsConfigurable
 *
 * @author oleg
 * @author Urs Wolfer
 */
open class GerritSettingsConfigurable : SearchableConfigurable, VcsConfigurableProvider {
    private var settingsPane: SettingsPanel? = null

    @Inject
    private lateinit var gerritSettings: GerritSettings

    @Inject
    private lateinit var gerritUpdatesNotificationComponent: GerritUpdatesNotificationComponent

    override fun getDisplayName(): String {
        return NAME
    }

    override fun getHelpTopic(): String {
        return "settings.gerrit"
    }

    override fun createComponent(): JComponent? {
        if (settingsPane == null) {
            settingsPane = GerritModule.getInstance<SettingsPanel>()
        }
        return settingsPane!!.panel
    }

    override fun isModified(): Boolean {
        return settingsPane?.let {
            !Comparing.equal(gerritSettings.login, it.login, true) ||
                isPasswordModified ||
                !Comparing.equal(gerritSettings.host, it.host, true) ||
                !Comparing.equal(gerritSettings.automaticRefresh, it.automaticRefresh) ||
                !Comparing.equal(gerritSettings.listAllChanges, it.listAllChanges) ||
                !Comparing.equal(gerritSettings.refreshTimeout, it.refreshTimeout) ||
                !Comparing.equal(gerritSettings.reviewNotifications, it.reviewNotifications) ||
                !Comparing.equal(gerritSettings.pushToGerrit, it.pushToGerrit) ||
                !Comparing.equal(gerritSettings.showChangeNumberColumn, it.showChangeNumberColumn) ||
                !Comparing.equal(gerritSettings.showChangeIdColumn, it.showChangeIdColumn) ||
                !Comparing.equal(gerritSettings.showTopicColumn, it.showTopicColumn) ||
                !Comparing.equal(gerritSettings.showProjectColumn, it.showProjectColumn) ||
                !Comparing.equal(gerritSettings.cloneBaseUrl, it.cloneBaseUrl, true) ||
                !Comparing.equal(gerritSettings.forceDefaultBranch, it.forceDefaultBranch)
        } ?: false
    }

    private val isPasswordModified: Boolean
        get() = settingsPane!!.isPasswordModified

    @Throws(ConfigurationException::class)
    override fun apply() {
        settingsPane?.let {
            gerritSettings.setLogin(it.login)
            if (isPasswordModified) {
                gerritSettings.setPassword(it.password)
                it.resetPasswordModification()
            }
            gerritSettings.host = it.host
            gerritSettings.listAllChanges = it.listAllChanges
            gerritSettings.automaticRefresh = it.automaticRefresh
            gerritSettings.refreshTimeout = it.refreshTimeout
            gerritSettings.reviewNotifications = it.reviewNotifications
            gerritSettings.pushToGerrit = it.pushToGerrit
            gerritSettings.showChangeNumberColumn = it.showChangeNumberColumn
            gerritSettings.showChangeIdColumn = it.showChangeIdColumn
            gerritSettings.showTopicColumn = it.showTopicColumn
            gerritSettings.showProjectColumn = it.showProjectColumn
            gerritSettings.cloneBaseUrl = it.cloneBaseUrl
            gerritSettings.forceDefaultBranch = it.forceDefaultBranch

            gerritUpdatesNotificationComponent.handleConfigurationChange()
        }
    }

    override fun reset() {
        settingsPane?.let {
            val login = gerritSettings.login
            it.login = login
            it.password = if (StringUtil.isEmptyOrSpaces(login)) "" else DEFAULT_PASSWORD_TEXT
            it.resetPasswordModification()
            it.host = gerritSettings.host
            it.listAllChanges = gerritSettings.listAllChanges
            it.automaticRefresh = gerritSettings.automaticRefresh
            it.refreshTimeout = gerritSettings.refreshTimeout
            it.reviewNotifications = gerritSettings.reviewNotifications
            it.pushToGerrit = gerritSettings.pushToGerrit
            it.showChangeNumberColumn = gerritSettings.showChangeNumberColumn
            it.showChangeIdColumn = gerritSettings.showChangeIdColumn
            it.showTopicColumn = gerritSettings.showTopicColumn
            it.showProjectColumn = gerritSettings.showProjectColumn
            it.cloneBaseUrl = gerritSettings.cloneBaseUrl
            it.forceDefaultBranch = gerritSettings.forceDefaultBranch
        }
    }

    override fun disposeUIResources() {
        settingsPane = null
    }

    override fun getId(): String {
        return helpTopic
    }

    override fun enableSearch(option: String): Runnable? {
        return null
    }

    override fun getConfigurable(project: Project): Configurable? {
        return this
    }

    class Proxy : GerritSettingsConfigurable() {
        private val delegate: GerritSettingsConfigurable = GerritModule.getInstance<GerritSettingsConfigurable>()

        override fun getDisplayName(): String {
            return delegate.displayName
        }

        override fun getHelpTopic(): String {
            return delegate.helpTopic
        }

        override fun createComponent(): JComponent? {
            return delegate.createComponent()
        }

        override fun isModified(): Boolean {
            return delegate.isModified
        }

        @Throws(ConfigurationException::class)
        override fun apply() {
            delegate.apply()
        }

        override fun reset() {
            delegate.reset()
        }

        override fun disposeUIResources() {
            delegate.disposeUIResources()
        }

        override fun getId(): String {
            return delegate.id
        }

        override fun enableSearch(option: String): Runnable? {
            return delegate.enableSearch(option)
        }

        override fun getConfigurable(project: Project): Configurable? {
            return delegate.getConfigurable(project)
        }
    }

    companion object {
        const val NAME: String = "Gerrit"
        private const val DEFAULT_PASSWORD_TEXT = "************"
    }
}
