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
package com.urswolfer.intellij.plugin.gerrit.ui

import com.google.common.base.Function
import com.google.common.base.Functions
import com.google.common.collect.*
import com.google.gerrit.extensions.common.*
import com.google.inject.Inject
import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.util.ui.ColumnInfo
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.util.RevisionInfos
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.border.Border
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Class representing the column in the [com.urswolfer.intellij.plugin.gerrit.ui.GerritChangeListPanel] to select
 * a revision for the row's change.
 *
 * @author Thomas Forrer
 */
class GerritSelectRevisionInfoColumn : ColumnInfo<ChangeInfo, String>("Patch Set") {
    @Inject
    private val selectedRevisions: SelectedRevisions? = null

    override fun valueOf(changeInfo: ChangeInfo): String {
        val activeRevision = selectedRevisions!![changeInfo] ?: return ""
        val revisionInfo = changeInfo.revisions[activeRevision]!!
        return getRevisionLabelFunction(changeInfo)(activeRevision to revisionInfo)
    }

    override fun isCellEditable(changeInfo: ChangeInfo): Boolean {
        return changeInfo.revisions != null && changeInfo.revisions.size > 1
    }

    override fun getEditor(changeInfo: ChangeInfo): TableCellEditor {
        val editor = createComboBoxTableRenderer(changeInfo)
        editor.addCellEditorListener(object : CellEditorListener {
            override fun editingStopped(e: ChangeEvent) {
                val cellEditor = e.source as ComboBoxTableRenderer<*>
                val value = cellEditor.cellEditorValue as String
                val pairs = changeInfo.revisions.entries.map { it.toPair() }
                val rev = getRevisionLabelFunction(changeInfo)
                val map: Map<String, Pair<String?, RevisionInfo?>> = pairs.associateBy { rev(it) }
                val pair = map[value]!!
                selectedRevisions!![changeInfo.id] = pair.first
            }

            override fun editingCanceled(e: ChangeEvent) {}
        })
        return editor
    }

    override fun getMaxStringValue(): String {
        return "99/99: abcedf1"
    }

    override fun getRenderer(changeInfo: ChangeInfo): TableCellRenderer {
        val renderer = createComboBoxTableRenderer(changeInfo)
        if (!isCellEditable(changeInfo)) {
            return object : DefaultTableCellRenderer() {
                override fun setBorder(border: Border) {
                    super.setBorder(renderer.border)
                }
            }
        }
        return renderer
    }

    private fun createComboBoxTableRenderer(changeInfo: ChangeInfo): ComboBoxTableRenderer<String> {
        val revisions = getRevisions(changeInfo)
        return object : ComboBoxTableRenderer<String>(revisions.toTypedArray()) {
            override fun isCellEditable(event: EventObject): Boolean {
                if (!this@GerritSelectRevisionInfoColumn.isCellEditable(changeInfo)) {
                    return false
                }
                if (event is MouseEvent) {
                    return event.clickCount >= 1
                }
                return false
            }
        }
    }

    private fun getRevisions(changeInfo: ChangeInfo): List<String> {
        if (changeInfo.revisions == null) {
            return emptyList()
        }
        val rev = getRevisionLabelFunction(changeInfo)
        return changeInfo.revisions.entries.toSortedSet(RevisionInfos.MAP_ENTRY_COMPARATOR)
            .map { it.toPair() }
            .map { rev(it) }
    }

    private fun getRevisionLabelFunction(changeInfo: ChangeInfo): (Pair<String, RevisionInfo>) -> String {
        return { revisionInfo: Pair<String, RevisionInfo> ->
            val size = changeInfo.revisions.size
            val number = revisionInfo.second._number
            val revision = revisionInfo.first.substring(0, 7)
            if (size < number) // size not available in older Gerrit versions
                "$number: $revision"
            else
                "$number/$size: $revision"
        }
    }
}
