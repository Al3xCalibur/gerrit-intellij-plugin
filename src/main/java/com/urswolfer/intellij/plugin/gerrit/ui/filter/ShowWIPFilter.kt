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
package com.urswolfer.intellij.plugin.gerrit.ui.filter

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * @author Guilherme Rodriguero
 */
class ShowWIPFilter : AbstractChangesFilter() {
    private var value = true

    override fun getAction(project: Project?): AnAction {
        return ShowWIPActionFilter()
    }

    private fun setValue(value: Boolean) {
        this.value = value
        setChanged()
        notifyObservers()
    }

    override val searchQueryPart: String?
        get() = if (value) null else "-is:wip"

    inner class ShowWIPActionFilter : ToggleAction("WIP Changes", "Display WIP changes", AllIcons.Actions.Profile),
        DumbAware, UpdateInBackground {
        override fun isSelected(e: AnActionEvent): Boolean {
            return value
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            setValue(state)
        }
    }
}
