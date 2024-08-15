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

import com.google.common.base.Joiner
import com.google.common.base.Optional
import com.google.common.base.Supplier
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
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
    private var value: Optional<Status>

    init {
        value = Optional.of(
            STATUSES[1]
        )
    }

    override fun getAction(project: Project?): AnAction {
        return StatusPopupAction(project, "Status")
    }

    override val searchQueryPart: String?
        get() = if (value.isPresent) {
            if (value.get().forQuery.isPresent) {
                String.format("is:%s", value.get().forQuery.get())
            } else {
                QUERY_FOR_ALL.get()
            }
        } else {
            null
        }

    private class Status(val label: String, forQuery: String?) {
        val forQuery: Optional<String> = Optional.fromNullable(forQuery)
    }

    inner class StatusPopupAction(private val project: Project?, labelText: String) : BasePopupAction(labelText) {
        init {
            updateFilterValueLabel(value.get().label)
        }

        override fun createActions(actionConsumer: Consumer<AnAction?>) {
            for (status in STATUSES) {
                actionConsumer.consume(object : DumbAwareAction(status.label) {
                    override fun actionPerformed(e: AnActionEvent) {
                        value = Optional.of(status)
                        updateFilterValueLabel(status.label)
                        setChanged()
                        notifyObservers(project)
                    }
                })
            }
        }
    }

    companion object {
        private val STATUSES: ImmutableList<Status> = ImmutableList.of(
            Status("All", null),
            Status("Open", "open"),
            Status("Merged", "merged"),
            Status("Abandoned", "abandoned"),
            Status("Drafts", "draft")
        )

        private val QUERY_FOR_ALL = Supplier {
            val queryForAll: MutableSet<String?> = Sets.newHashSet()
            for (status in STATUSES) {
                if (status.forQuery.isPresent) {
                    queryForAll.add(String.format("is:%s", status.forQuery.get()))
                }
            }
            String.format("(%s)", Joiner.on("+OR+").join(queryForAll))
        }
    }
}
