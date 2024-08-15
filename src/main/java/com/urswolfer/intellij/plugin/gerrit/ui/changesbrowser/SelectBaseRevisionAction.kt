/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser

import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.RevisionInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.util.Consumer
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction
import com.urswolfer.intellij.plugin.gerrit.util.RevisionInfos
import java.util.*

/**
 * @author Thomas Forrer
 */
class SelectBaseRevisionAction(private val selectedRevisions: SelectedRevisions) : BasePopupAction("Diff against") {
    private var selectedChange: ChangeInfo? = null
    private var selectedValue: Pair<String, RevisionInfo>? = null
    private val listeners: MutableList<Listener> = mutableListOf()

    init {
        selectedRevisions.addObserver { o: Observable?, arg: Any? ->
            val value = selectedValue
            if (arg is String && value != null) {
                val selectedRevision = selectedRevisions[arg]
                if (selectedRevision != null && selectedRevision == value.first) {
                    removeSelectedValue()
                    updateLabel()
                }
            }
        }
        updateLabel()
    }

    override fun createActions(anActionConsumer: Consumer<AnAction?>) {
        anActionConsumer.consume(object : DumbAwareUpdateInBackgroundAction(BASE) {
            override fun actionPerformed(e: AnActionEvent) {
                removeSelectedValue()
                updateLabel()
            }
        })
        val selectedChange = selectedChange ?: return
        val revisions = selectedChange.revisions.entries.toSortedSet(RevisionInfos.MAP_ENTRY_COMPARATOR)
        for (entry in revisions) {
            anActionConsumer.consume(getActionForRevision(entry.key, entry.value))
        }
    }

    fun setSelectedChange(selectedChange: ChangeInfo?) {
        this.selectedChange = selectedChange
        selectedValue = null
        updateLabel()
    }

    fun addRevisionSelectedListener(listener: Listener) {
        listeners.add(listener)
    }

    private fun getActionForRevision(commitHash: String, revisionInfo: RevisionInfo): DumbAwareAction {
        val infoPair = commitHash to revisionInfo
        val actionLabel = REVISION_LABEL_FUNCTION(infoPair)
        return object : DumbAwareUpdateInBackgroundAction(actionLabel) {
            override fun actionPerformed(e: AnActionEvent) {
                updateSelectedValue(infoPair)
                updateLabel()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = !isSameRevisionAsSelected
            }

            private val isSameRevisionAsSelected: Boolean
                get() = selectedChange != null && commitHash == selectedRevisions[selectedChange!!]
        }
    }

    private fun updateSelectedValue(revisionInfo: Pair<String, RevisionInfo>) {
        selectedValue = revisionInfo
        notifyListeners()
    }

    private fun removeSelectedValue() {
        selectedValue = null
        notifyListeners()
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            listener.revisionSelected(selectedValue)
        }
    }

    private fun updateLabel() {
        updateFilterValueLabel(REVISION_LABEL_FUNCTION(selectedValue))
    }

    fun interface Listener {
        fun revisionSelected(revisionInfo: Pair<String, RevisionInfo>?)
    }

    private abstract inner class DumbAwareUpdateInBackgroundAction protected constructor(text: @ActionText String?) :
        DumbAwareAction(text), UpdateInBackground

    companion object {
        private const val BASE = "Base"
        private val REVISION_LABEL_FUNCTION: (Pair<String, RevisionInfo>?) -> String = { revisionInfo ->
            "${revisionInfo?.second?._number}: ${revisionInfo?.first?.substring(0, 7)}"
        }
    }
}
