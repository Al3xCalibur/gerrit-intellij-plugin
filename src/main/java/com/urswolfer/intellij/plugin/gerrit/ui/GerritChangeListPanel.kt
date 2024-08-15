/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.urswolfer.intellij.plugin.gerrit.ui

import com.google.common.base.Splitter
import com.google.common.collect.*
import com.google.gerrit.extensions.client.ChangeStatus
import com.google.gerrit.extensions.common.*
import com.google.inject.Inject
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.TableView
import com.intellij.util.Consumer
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy
import git4idea.GitUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.AdjustmentEvent
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import kotlin.concurrent.Volatile
import kotlin.math.min

/**
 * A table with the list of changes.
 * Parts based on git4idea.ui.GitCommitListPanel
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
class GerritChangeListPanel @Inject constructor(
    private val selectedRevisions: SelectedRevisions,
    private val selectRevisionInfoColumn: GerritSelectRevisionInfoColumn,
    private val gerritSettings: GerritSettings,
    private val showSettingsUtil: ShowSettingsUtil
) : JPanel(), Consumer<LoadChangesProxy> {
    private val changes: MutableList<ChangeInfo> = Lists.newArrayList()

    val table: TableView<ChangeInfo> = TableView()
    private var loadChangesProxy: LoadChangesProxy? = null

    private var project: Project? = null

    @Volatile
    private var loadingMoreChanges = false
    private val scrollPane: JScrollPane

    init {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        PopupHandler.installPopupHandler(table, "Gerrit.ListPopup", ActionPlaces.UNKNOWN)

        updateModel(changes)
        table.isStriped = true

        layout = BorderLayout()
        scrollPane = ScrollPaneFactory.createScrollPane(table)
        scrollPane.verticalScrollBar.addAdjustmentListener { e: AdjustmentEvent ->
            if (!loadingMoreChanges && loadChangesProxy != null) {
                loadingMoreChanges = true
                try {
                    val lowerEnd = e.adjustable.visibleAmount + e.adjustable.value
                    if (lowerEnd == e.adjustable.maximum) {
                        loadChangesProxy!!.getNextPage { changes: List<ChangeInfo> -> this.addChanges(changes) }
                    }
                } finally {
                    loadingMoreChanges = false
                }
            }
        }
        add(scrollPane)
    }

    fun setProject(project: Project?) {
        this.project = project
    }

    override fun consume(proxy: LoadChangesProxy) {
        loadChangesProxy = proxy
        proxy.getNextPage { changeInfos ->
            setChanges(changeInfos)
            setupEmptyTableHint()
        }
    }

    private fun setupEmptyTableHint() {
        val emptyText = table.emptyText
        emptyText.clear()
        emptyText.appendText(
            "No changes to display. " +
                    "If you expect changes, there might be a configuration issue. " +
                    "Click "
        )
        emptyText.appendText(
            "here",
            SimpleTextAttributes.LINK_ATTRIBUTES
        ) { actionEvent: ActionEvent? -> BrowserUtil.browse("https://github.com/uwolfer/gerrit-intellij-plugin#list-of-changes-is-empty") }
        emptyText.appendText(" for hints.")
    }

    fun showSetupHintWhenRequired(project: Project?) {
        if (!gerritSettings.isLoginAndPasswordAvailable) {
            val emptyText = table.emptyText
            emptyText.appendText("Open ")
            emptyText.appendText(
                "settings",
                SimpleTextAttributes.LINK_ATTRIBUTES
            ) { actionEvent: ActionEvent? ->
                showSettingsUtil.showSettingsDialog(
                    project,
                    GerritSettingsConfigurable.Companion.NAME
                )
            }
            emptyText.appendText(" to configure this plugin and press the refresh button afterwards.")
        }
    }

    /**
     * Adds a listener that would be called once user selects a change in the table.
     */
    fun addListSelectionListener(listener: Consumer<ChangeInfo>) {
        table.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
            val lsm = e.source as ListSelectionModel
            val i = lsm.maxSelectionIndex
            if (i >= 0 && !e.valueIsAdjusting) {
                listener.consume(changes[i])
            }
        }
    }

    fun setChanges(changes: List<ChangeInfo>) {
        this.changes.clear()
        this.changes.addAll(changes)
        initModel()
        table.repaint()
        selectedRevisions.clear()
    }

    fun addChanges(changes: List<ChangeInfo>) {
        this.changes.addAll(changes)
        // did not find another way to update the scrollbar after adding more changes...
        scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.value - 1
    }

    private fun initModel() {
        table.setModelAndUpdateColumns(ListTableModel(generateColumnsInfo(changes), changes, 0))
    }

    private fun updateModel(changes: List<ChangeInfo>) {
        table.listTableModel.addRows(changes)
    }

    private fun generateColumnsInfo(changes: List<ChangeInfo>): Array<ColumnInfo<*, *>> {
        var number = ItemAndWidth("", 0)
        var hash = ItemAndWidth("", 0)
        var topic = ItemAndWidth("", 0)
        var subject = ItemAndWidth("", 0)
        var status = ItemAndWidth("", 0)
        var author = ItemAndWidth("", 0)
        var projectName = ItemAndWidth("", 0)
        var branch = ItemAndWidth("", 0)
        var time = ItemAndWidth("", 0)
        val availableLabels: MutableSet<String> = Sets.newTreeSet()
        for (change in changes) {
            number = getMax(number, getNumber(change))
            hash = getMax(hash, getHash(change))
            topic = getMax(topic, getTopic(change))
            subject = getMax(subject, getShortenedSubject(change))
            status = getMax(status, getStatus(change))
            author = getMax(author, getOwner(change))
            projectName = getMax(projectName, getProject(change))
            branch = getMax(branch, getBranch(change))
            time = getMax(time, getTime(change))
            if (change.labels != null) {
                availableLabels.addAll(change.labels.keys)
            }
        }

        val columnList: MutableList<ColumnInfo<*, *>> = Lists.newArrayList()
        columnList.add(GerritChangeColumnStarredInfo())
        val showChangeNumberColumn = gerritSettings.showChangeNumberColumn
        if (showChangeNumberColumn) {
            columnList.add(
                object : GerritChangeColumnInfo("#", number.item) {
                    override fun valueOf(change: ChangeInfo): String {
                        return getNumber(change)
                    }
                }
            )
        }
        val showChangeIdColumn = gerritSettings.showChangeIdColumn
        if (showChangeIdColumn) {
            columnList.add(
                object : GerritChangeColumnInfo("ID", hash.item) {
                    override fun valueOf(change: ChangeInfo): String {
                        return getHash(change)
                    }
                }
            )
        }
        val showTopicColumn = gerritSettings.showTopicColumn
        if (showTopicColumn) {
            columnList.add(
                object : GerritChangeColumnInfo("Topic", topic.item) {
                    override fun valueOf(change: ChangeInfo): String {
                        return getTopic(change)
                    }
                }
            )
        }

        columnList.add(
            object : GerritChangeColumnInfo("Subject", subject.item) {
                override fun valueOf(change: ChangeInfo): String? {
                    return change.subject
                }

                override fun getPreferredStringValue(): String? {
                    return super.getMaxStringValue()
                }

                override fun getMaxStringValue(): String? {
                    return null // allow to use remaining space
                }
            }
        )
        columnList.add(
            object : GerritChangeColumnInfo("Status", status.item) {
                override fun valueOf(change: ChangeInfo): String {
                    return getStatus(change)
                }
            }
        )
        columnList.add(
            object : GerritChangeColumnInfo("Owner", author.item) {
                override fun valueOf(change: ChangeInfo): String {
                    return getOwner(change)
                }

                override fun getRenderer(changeInfo: ChangeInfo): TableCellRenderer {
                    return object : DefaultTableCellRenderer() {
                        override fun getTableCellRendererComponent(
                            table: JTable,
                            value: Any,
                            isSelected: Boolean,
                            hasFocus: Boolean,
                            row: Int,
                            column: Int
                        ): Component {
                            val labelComponent = super.getTableCellRendererComponent(
                                table,
                                value,
                                isSelected,
                                hasFocus,
                                row,
                                column
                            ) as JLabel
                            labelComponent.toolTipText = getAccountTooltip(changeInfo.owner)
                            return labelComponent
                        }
                    }
                }
            }
        )
        val showProjectColumn = gerritSettings.showProjectColumn
        val listAllChanges = gerritSettings.listAllChanges
        if (showProjectColumn == ShowProjectColumn.ALWAYS
            || (showProjectColumn == ShowProjectColumn.AUTO && (listAllChanges || hasProjectMultipleRepos()))
        ) {
            columnList.add(
                object : GerritChangeColumnInfo("Project", projectName.item) {
                    override fun valueOf(change: ChangeInfo): String {
                        return getProject(change)
                    }
                }
            )
        }
        columnList.add(
            object : GerritChangeColumnInfo("Branch", branch.item) {
                override fun valueOf(change: ChangeInfo): String {
                    return getBranch(change)
                }
            }
        )
        columnList.add(
            object : GerritChangeColumnInfo("Updated", time.item) {
                override fun valueOf(change: ChangeInfo): String {
                    return getTime(change)
                }
            }
        )
        for (label in availableLabels) {
            columnList.add(
                object : GerritChangeColumnIconLabelInfo(getShortLabelDisplay(label), label) {
                    override fun getLabelInfo(change: ChangeInfo): LabelInfo? {
                        return getLabel(change, label)
                    }
                }
            )
        }
        columnList.add(selectRevisionInfoColumn)

        return columnList.toTypedArray<ColumnInfo<*, *>>()
    }

    private fun hasProjectMultipleRepos(): Boolean {
        if (project == null) {
            return false
        }
        val repositoryManager = GitUtil.getRepositoryManager(project!!)
        return repositoryManager.repositories.size > 1
    }

    /**
     * Builds "Gerrit-like" short display of label:
     * Code-Review -> CR: collect first letter of every word part.
     */
    private fun getShortLabelDisplay(label: String): String {
        var result = ""
        val parts = Splitter.on('-').omitEmptyStrings().split(label)
        for (part in parts) {
            result += part.substring(0, 1)
        }
        return result
    }

    private fun getMax(current: ItemAndWidth, candidate: String?): ItemAndWidth {
        if (candidate == null) {
            return current
        }
        val width = table.getFontMetrics(table.font).stringWidth(candidate)
        if (width > current.width) {
            return ItemAndWidth(candidate, width)
        }
        return current
    }

    private class ItemAndWidth(val item: String, val width: Int)

    private abstract class GerritChangeColumnInfo(name: String, private val maxString: String) :
        ColumnInfo<ChangeInfo, String?>(name) {
        override fun getMaxStringValue(): String? {
            return maxString
        }

        override fun getAdditionalWidth(): Int {
            return UIUtil.DEFAULT_HGAP
        }
    }

    private abstract class GerritChangeColumnIconLabelInfo(shortLabel: String?, private val label: String) :
        ColumnInfo<ChangeInfo, LabelInfo?>(shortLabel) {
        override fun valueOf(changeInfo: ChangeInfo): LabelInfo? {
            return null
        }

        abstract fun getLabelInfo(change: ChangeInfo): LabelInfo?

        override fun getTooltipText(): String? {
            return label
        }

        override fun getRenderer(changeInfo: ChangeInfo): TableCellRenderer? {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    val labelComponent =
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                    val labelInfo = getLabelInfo(changeInfo)
                    labelComponent.icon = getIconForLabel(labelInfo)
                    labelComponent.toolTipText = getToolTipForLabel(labelInfo)
                    labelComponent.horizontalAlignment = CENTER
                    labelComponent.verticalAlignment = CENTER
                    return labelComponent
                }
            }
        }

        override fun getWidth(table: JTable): Int {
            return AllIcons.Actions.Checked.iconWidth + 20
        }

        companion object {
            private fun getIconForLabel(labelInfo: LabelInfo?): Icon? {
                if (labelInfo != null) {
                    if (labelInfo.rejected != null) {
                        return AllIcons.Actions.Cancel
                    }
                    if (labelInfo.approved != null) {
                        return AllIcons.Actions.Checked
                    }
                    if (labelInfo.disliked != null) {
                        return AllIcons.Actions.MoveDown
                    }
                    if (labelInfo.recommended != null) {
                        return AllIcons.Actions.MoveUp
                    }
                }
                return null
            }

            private fun getToolTipForLabel(labelInfo: LabelInfo?): String? {
                if (labelInfo != null) {
                    var accountInfo: AccountInfo? = null
                    if (labelInfo.rejected != null) {
                        accountInfo = labelInfo.rejected
                    }
                    if (labelInfo.approved != null) {
                        accountInfo = labelInfo.approved
                    }
                    if (labelInfo.disliked != null) {
                        accountInfo = labelInfo.disliked
                    }
                    if (labelInfo.recommended != null) {
                        accountInfo = labelInfo.recommended
                    }
                    if (accountInfo != null) {
                        return getAccountTooltip(accountInfo)
                    }
                }
                return null
            }
        }
    }

    private class GerritChangeColumnStarredInfo : ColumnInfo<ChangeInfo, Boolean?>("") {
        override fun valueOf(changeInfo: ChangeInfo): Boolean? {
            return null
        }

        override fun getRenderer(changeInfo: ChangeInfo): TableCellRenderer? {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    val label =
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                    if (changeInfo.starred != null && changeInfo.starred) {
                        label.icon = AllIcons.Nodes.Favorite
                    }
                    label.horizontalAlignment = CENTER
                    label.verticalAlignment = CENTER
                    return label
                }
            }
        }

        override fun getWidth(table: JTable): Int {
            return AllIcons.Nodes.Favorite.iconWidth
        }
    }

    companion object {
        private fun getNumber(change: ChangeInfo): String {
            return change._number.toString()
        }

        private fun getHash(change: ChangeInfo): String {
            return change.changeId.substring(0, min(change.changeId.length.toDouble(), 9.0).toInt())
        }

        private fun getTopic(change: ChangeInfo): String {
            return change.topic
        }

        private fun getShortenedSubject(change: ChangeInfo): String {
            return change.subject.substring(0, min(change.subject.length.toDouble(), 80.0).toInt())
        }

        private fun getStatus(change: ChangeInfo): String {
            if (ChangeStatus.MERGED == change.status) {
                return "Merged"
            }
            if (ChangeStatus.ABANDONED == change.status) {
                return "Abandoned"
            }
            if (change.mergeable != null && !change.mergeable) {
                return "Merge Conflict"
            }
            if (ChangeStatus.DRAFT == change.status) {
                return "Draft"
            }
            return ""
        }

        private fun getOwner(change: ChangeInfo): String {
            return change.owner.name
        }

        private fun getAccountTooltip(accountInfo: AccountInfo): String {
            return if (accountInfo.email != null) {
                "${accountInfo.name} &lt;${accountInfo.email}&gt;"
            } else {
                accountInfo.name
            }
        }

        private fun getProject(change: ChangeInfo): String {
            return change.project
        }

        private fun getBranch(change: ChangeInfo): String {
            return change.branch
        }

        private fun getTime(change: ChangeInfo): String {
            return if (change.updated != null) DateFormatUtil.formatPrettyDateTime(change.updated) else ""
        }

        private fun getLabel(change: ChangeInfo, labelName: String): LabelInfo? {
            val labels = change.labels
            return labels?.get(labelName)
        }
    }
}
