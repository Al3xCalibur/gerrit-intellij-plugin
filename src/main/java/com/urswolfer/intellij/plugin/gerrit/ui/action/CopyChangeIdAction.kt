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
package com.urswolfer.intellij.plugin.gerrit.ui.action

import com.google.inject.Inject
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.util.*
import java.awt.datatransfer.StringSelection

/**
 * @author Wurstmeister
 */
// proxy class below is registered
open class CopyChangeIdAction : AbstractChangeAction("Copy", "Copy Change-ID", AllIcons.Actions.Copy) {
    @Inject
    private lateinit var notificationService: NotificationService

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val changeDetails = getSelectedChange(anActionEvent) ?: return

        val stringToCopy = changeDetails.changeId
        CopyPasteManager.getInstance().setContents(StringSelection(stringToCopy))
        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)
        val builder = NotificationBuilder(project, "Copy", "Copied Change-ID to clipboard.")
        notificationService.notify(builder)
    }

    class Proxy : CopyChangeIdAction() {
        private val delegate: CopyChangeIdAction = GerritModule.getInstance<CopyChangeIdAction>()

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
