/*
 * Copyright 2013-2016 Urs Wolfer
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

import com.google.common.collect.*
import com.google.inject.Inject
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import java.util.*
import javax.swing.Icon

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class ReviewActionGroup : ActionGroup("Review", "Review Change", AllIcons.Debugger.Watch), UpdateInBackground {
    @Inject
    private lateinit var reviewActionFactory: ReviewActionFactory

    @Inject
    private lateinit var gerritSettings: GerritSettings

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = gerritSettings.isLoginAndPasswordAvailable
    }

    override fun getChildren(anActionEvent: AnActionEvent?): Array<AnAction> {
        val selectedChange = ActionUtil.getSelectedChange(anActionEvent) ?: return emptyArray<AnAction>()

        val permittedLabels = selectedChange.permittedLabels
        val labels: MutableList<AnAction> = Lists.newArrayList()
        if (permittedLabels != null) {
            for (entry in permittedLabels.entries) {
                labels.add(createLabelGroup(entry))
            }
        }
        return labels.toTypedArray<AnAction>()
    }

    private fun createLabelGroup(entry: Map.Entry<String, Collection<String>>): ActionGroup {
        return object : ActionGroup(entry.key, true) {
            override fun getChildren(anActionEvent: AnActionEvent?): Array<AnAction> {
                val valueActions: MutableList<AnAction> = Lists.newArrayList()
                val values: Collection<String> = entry.value
                val intValues: List<Int> = values.map {
                    val x = it.trim { it <= ' ' }
                    (if (x[0] == '+') x.substring(1) else x).toInt() // required for Java 6 support
                }.sortedDescending()
                for (value in intValues) {
                    valueActions.add(reviewActionFactory.get(entry.key, value, ICONS[value], false))
                    valueActions.add(reviewActionFactory.get(entry.key, value, ICONS[value], true))
                }
                return valueActions.toTypedArray<AnAction>()
            }
        }
    }

    class Proxy : ReviewActionGroup() {
        private val delegate: ReviewActionGroup = GerritModule.getInstance<ReviewActionGroup>()

        override fun update(e: AnActionEvent) {
            delegate.update(e)
        }

        override fun getChildren(anActionEvent: AnActionEvent?): Array<AnAction> {
            return delegate.getChildren(anActionEvent)
        }
    }

    companion object {
        private val ICONS: ImmutableMap<Int, Icon> = ImmutableMap.of(
            -2, AllIcons.Actions.Cancel,
            -1, AllIcons.Actions.MoveDown,
            0, AllIcons.Actions.Forward,
            1, AllIcons.Actions.MoveUp,
            2, AllIcons.Actions.Checked
        )
    }
}
