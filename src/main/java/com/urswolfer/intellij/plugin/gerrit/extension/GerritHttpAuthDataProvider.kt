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
package com.urswolfer.intellij.plugin.gerrit.extension

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.AuthData
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import git4idea.remote.GitHttpAuthDataProvider

/**
 * Parts based on org.jetbrains.plugins.github.extensions.GithubHttpAuthDataProvider
 *
 * @author Urs Wolfer
 * @author Kirill Likhodedov
 */
class GerritHttpAuthDataProvider @Inject constructor(private val gerritSettings: GerritSettings) : GitHttpAuthDataProvider {
    override fun getAuthData(project: Project, url: String): AuthData? {
        if (!gerritSettings.host.equals(url, ignoreCase = true)) {
            return null
        }
        val password = gerritSettings.password
        if (StringUtil.isEmptyOrSpaces(gerritSettings.login) || StringUtil.isEmptyOrSpaces(password)) {
            return null
        }
        return AuthData(gerritSettings.login, password)
    }

    override fun forgetPassword(project: Project, url: String, authData: AuthData) {
        if (gerritSettings.host.equals(url, ignoreCase = true)) {
            gerritSettings.forgetPassword()
        }
    }

    class Proxy : GitHttpAuthDataProvider {
        private val delegate: GitHttpAuthDataProvider = GerritModule.getInstance<GerritHttpAuthDataProvider>()

        override fun getAuthData(project: Project, url: String): AuthData? {
            return delegate.getAuthData(project, url)
        }

        override fun getAuthData(project: Project, url: String, login: String): AuthData? {
            return delegate.getAuthData(project, url, login)
        }

        override fun forgetPassword(project: Project, url: String, authData: AuthData) {
            delegate.forgetPassword(project, url, authData)
        }
    }
}
