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
 * @author Thomas Forrer
 */
class IsStarredFilter : AbstractChangesFilter() {
    private var value = false

    override fun getAction(project: Project?): AnAction {
        return IsStarredAction()
    }

    private fun setValue(value: Boolean) {
        this.value = value
        setChanged()
        notifyObservers()
    }

    override val searchQueryPart: String?
        get() = if (value) "is:starred" else null

    inner class IsStarredAction : ToggleAction("Starred changes", "Show only starred changes", AllIcons.Nodes.Favorite),
        DumbAware, UpdateInBackground {
        override fun isSelected(e: AnActionEvent): Boolean {
            return value
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            setValue(state)
        }
    }
}
