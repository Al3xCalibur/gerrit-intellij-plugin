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
package com.urswolfer.intellij.plugin.gerrit

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.google.inject.*
import com.intellij.openapi.application.ApplicationManager
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.intellij.plugin.gerrit.extension.GerritCheckoutProvider
import com.urswolfer.intellij.plugin.gerrit.extension.GerritHttpAuthDataProvider
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtension
import com.urswolfer.intellij.plugin.gerrit.rest.GerritRestModule
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindow
import com.urswolfer.intellij.plugin.gerrit.ui.GerritUiModule
import com.urswolfer.intellij.plugin.gerrit.ui.action.GerritActionsModule
import com.urswolfer.intellij.plugin.gerrit.ui.diff.GerritDiffModule
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService
import com.urswolfer.intellij.plugin.gerrit.util.UtilsModule

/**
 * @author Thomas Forrer
 */
open class GerritModule protected constructor() : AbstractModule() {
    override fun configure() {
        installOpenIdeDependenciesModule()

        setupSettingsProvider()

        bind(NotificationService::class.java)
        bind(SelectedRevisions::class.java).toInstance(SelectedRevisions())

        bind(GerritGitUtil::class.java)
        bind(GerritUtil::class.java)

        bind(GerritToolWindow::class.java)
        bind(GerritCheckoutProvider::class.java)
        bind(GerritHttpAuthDataProvider::class.java)
        bind(GerritPushExtension::class.java)

        install(UtilsModule())
        install(GerritActionsModule())
        install(GerritDiffModule())
        install(GerritRestModule())
        install(GerritUiModule())
    }

    protected open fun setupSettingsProvider() {
        val settingsProvider = Provider {
            // GerritSettings instance needs to be retrieved from ServiceManager, need to inject the Logger manually...
            val gerritSettings = ApplicationManager.getApplication().getService(
                GerritSettings::class.java
            )
            gerritSettings.setLog(OpenIdeDependenciesModule.LOG)
            gerritSettings
        }
        bind(GerritSettings::class.java).toProvider(settingsProvider).`in`(Singleton::class.java)
        bind(GerritAuthData::class.java).toProvider(settingsProvider).`in`(Singleton::class.java)
    }

    protected open fun installOpenIdeDependenciesModule() {
        install(OpenIdeDependenciesModule())
    }

    companion object {
        val injector: Supplier<Injector> = Suppliers.memoize { Guice.createInjector(GerritModule()) }

        inline fun <reified T> getInstance(): T {
            return injector.get().getInstance(T::class.java)
        }
    }
}
