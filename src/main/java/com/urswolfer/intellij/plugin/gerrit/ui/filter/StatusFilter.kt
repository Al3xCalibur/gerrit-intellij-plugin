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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction

/**
 * @author Thomas Forrer
 */
class StatusFilter : AbstractChangesFilter() {
    private var value: Status = STATUSES[1]

    override fun getAction(project: Project?): AnAction {
        return StatusPopupAction(project, "Status")
    }

    override val searchQueryPart: String
        get() =
            if (value.forQuery != null) {
                "is:${value.forQuery}"
            } else {
                QUERY_FOR_ALL.value
            }

    private class Status(val label: String, val forQuery: String?)

    inner class StatusPopupAction(private val project: Project?, labelText: String) : BasePopupAction(labelText) {
        init {
            updateFilterValueLabel(value.label)
        }

        override fun createActions(actionConsumer: Consumer<AnAction?>) {
            for (status in STATUSES) {
                actionConsumer.consume(object : DumbAwareAction(status.label) {
                    override fun actionPerformed(e: AnActionEvent) {
                        value = status
                        updateFilterValueLabel(status.label)
                        setChanged()
                        notifyObservers(project)
                    }
                })
            }
        }
    }

    companion object {
        private val STATUSES = listOf(
            Status("All", null),
            Status("Open", "open"),
            Status("Merged", "merged"),
            Status("Abandoned", "abandoned"),
            Status("Drafts", "draft")
        )

        private val QUERY_FOR_ALL = lazy {
            val queryForAll: MutableSet<String?> = mutableSetOf()
            for (status in STATUSES) {
                if (status.forQuery != null) {
                    queryForAll.add("is:${status.forQuery}")
                }
            }
            queryForAll.joinToString("+OR+", "(", ")")
        }
    }
}
