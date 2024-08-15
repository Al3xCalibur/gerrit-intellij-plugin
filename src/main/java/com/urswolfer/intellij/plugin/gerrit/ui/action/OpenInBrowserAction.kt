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
package com.urswolfer.intellij.plugin.gerrit.ui.action

import com.google.gerrit.extensions.common.*
import com.google.inject.Inject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import icons.MyIcons

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class OpenInBrowserAction :
    AbstractChangeAction("Open in Gerrit", "Open corresponding link in browser", MyIcons.Gerrit) {
    @Inject
    private lateinit var gerritSettings: GerritSettings

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val selectedChange = getSelectedChange(anActionEvent) ?: return

        val urlToOpen = getUrl(selectedChange)
        BrowserUtil.browse(urlToOpen)
    }

    private fun getUrl(change: ChangeInfo): String {
        val url = gerritSettings.host
        val changeNumber = change._number
        return "$url/$changeNumber"
    }

    class Proxy : OpenInBrowserAction() {
        private val delegate: OpenInBrowserAction = GerritModule.getInstance<OpenInBrowserAction>()

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
