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
package com.urswolfer.intellij.plugin.gerrit.push

import com.google.common.base.Joiner
import com.google.common.base.Optional
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.UIUtil
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * @author Urs Wolfer
 */
class GerritPushExtensionPanel(private val pushToGerritByDefault: Boolean, private val forceDefaultBranch: Boolean) :
    JPanel() {
    private var indentedSettingPanel: JPanel? = null

    private var pushToGerritCheckBox: JCheckBox? = null
    private var privateCheckBox: JCheckBox? = null
    private var unmarkPrivateCheckBox: JCheckBox? = null
    private var publishDraftCommentsCheckBox: JCheckBox? = null
    private var wipCheckBox: JCheckBox? = null
    private var draftChangeCheckBox: JCheckBox? = null
    private var submitChangeCheckBox: JCheckBox? = null
    private var readyCheckBox: JCheckBox? = null
    private var branchTextField: JTextField? = null
    private var topicTextField: JTextField? = null
    private var hashTagTextField: JTextField? = null
    private var reviewersTextField: JTextField? = null
    private var ccTextField: JTextField? = null
    private var patchsetDescriptionTextField: JTextField? = null
    private val gerritPushTargetPanels: MutableMap<GerritPushTargetPanel, String?> = Maps.newHashMap()
    private var initialized = false

    init {
        createLayout()

        pushToGerritCheckBox!!.isSelected = pushToGerritByDefault
        pushToGerritCheckBox!!.addActionListener(SettingsStateActionListener())
        setSettingsEnabled(pushToGerritCheckBox!!.isSelected)

        addChangeListener()
    }

    fun registerGerritPushTargetPanel(gerritPushTargetPanel: GerritPushTargetPanel, branch: String?) {
        var branch = branch
        if (initialized) { // a new dialog gets initialized; start again
            initialized = false
            gerritPushTargetPanels.clear()
        }

        if (branch != null) {
            branch = branch.replace("^refs/(for|drafts)/".toRegex(), "")
            branch = branch.replace("%.*$".toRegex(), "")
        }

        gerritPushTargetPanels[gerritPushTargetPanel] = branch
    }

    fun initialized() {
        initialized = true

        // force a deferred update (changes are monitored only after full construction of dialog)
        SwingUtilities.invokeLater {
            if (forceDefaultBranch) {
                val gitReviewBranchName = gitReviewBranchName
                if (gitReviewBranchName.isPresent) {
                    branchTextField!!.text = gitReviewBranchName.get()
                }
            } else if (gerritPushTargetPanels.size == 1) {
                val branchName = gerritPushTargetPanels.values.iterator().next()
                val gitReviewBranchName = gitReviewBranchName
                branchTextField!!.text = gitReviewBranchName.or(branchName)
            }
            initDestinationBranch()
        }
    }

    private val gitReviewBranchName: Optional<String?>
        get() {
            var branchName = Optional.absent<String?>()

            val dataContext = DataManager.getInstance().getDataContext(this)
            val openedProject = Optional.fromNullable(CommonDataKeys.PROJECT.getData(dataContext))

            if (openedProject.isPresent) {
                val gitReviewFilePath = Joiner.on(File.separator).join(
                    openedProject.get().basePath, GITREVIEW_FILENAME
                )

                val gitReviewFile = File(gitReviewFilePath)
                if (gitReviewFile.exists() && gitReviewFile.isFile) {
                    try {
                        FileInputStream(gitReviewFilePath).use { fileInputStream ->
                            val properties = Properties()
                            properties.load(fileInputStream)
                            branchName =
                                Optional.fromNullable(Strings.emptyToNull(properties.getProperty("defaultbranch")))
                        }
                    } catch (e: IOException) {
                        //no need to handle as branch name is already absent and ready to be returned
                    }
                    //no need to handle as branch name is already absent and ready to be returned
                }
            }

            return branchName
        }

    private fun createLayout() {
        val mainPanel = JPanel()
        mainPanel.alignmentX = LEFT_ALIGNMENT
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        pushToGerritCheckBox = JCheckBox("Push to Gerrit")
        mainPanel.add(pushToGerritCheckBox)

        indentedSettingPanel = JPanel(GridLayoutManager(13, 2))

        privateCheckBox = JCheckBox("Private (Gerrit 2.15+)")
        privateCheckBox!!.toolTipText = "Push a private change or to turn a change private."
        indentedSettingPanel!!.add(
            privateCheckBox,
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null
            )
        )

        unmarkPrivateCheckBox = JCheckBox("Unmark Private (Gerrit 2.15+)")
        unmarkPrivateCheckBox!!.toolTipText = "Unmark an existing change private."
        indentedSettingPanel!!.add(
            unmarkPrivateCheckBox,
            GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null
            )
        )

        wipCheckBox = JCheckBox("WIP (Work-In-Progress Changes) (Gerrit 2.15+)")
        wipCheckBox!!.toolTipText = "Push a wip change or to turn a change to wip."
        indentedSettingPanel!!.add(
            wipCheckBox,
            GridConstraints(
                3,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null
            )
        )

        readyCheckBox = JCheckBox("Ready (Gerrit 2.15+)")
        readyCheckBox!!.toolTipText = "Mark a Work-In-Progress Change as Ready for review"
        indentedSettingPanel!!.add(
            readyCheckBox,
            GridConstraints(
                4,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null
            )
        )

        publishDraftCommentsCheckBox = JCheckBox("Publish Draft Comments (Gerrit 2.15+)")
        publishDraftCommentsCheckBox!!.toolTipText =
            "If you have draft comments on the change(s) that are updated by the push, the publish-comments option will cause them to be published."
        indentedSettingPanel!!.add(
            publishDraftCommentsCheckBox,
            GridConstraints(
                1,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null
            )
        )

        draftChangeCheckBox = JCheckBox("Draft-Change (Gerrit older than 2.15)")
        draftChangeCheckBox!!.toolTipText = "Publish change as draft (reviewers cannot submit change)."
        indentedSettingPanel!!.add(
            draftChangeCheckBox,
            GridConstraints(
                2,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null
            )
        )

        submitChangeCheckBox = JCheckBox("Submit Change")
        submitChangeCheckBox!!.toolTipText =
            "Changes can be directly submitted on push. This is primarily useful for " +
                    "teams that don't want to do code review but want to use Gerrit’s submit strategies to handle " +
                    "contention on busy branches. Using submit creates a change and submits it immediately, if the caller " + "has submit permission."
        indentedSettingPanel!!.add(
            submitChangeCheckBox,
            GridConstraints(
                3,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null
            )
        )

        branchTextField = addTextField(
            "Branch:",
            "The push destination branch.",
            7
        )

        topicTextField = addTextField(
            "Topic:",
            "A short topic associated with all of the changes in the same group, such as the local topic branch name.",
            8
        )

        hashTagTextField = addTextField(
            "Hashtag (Gerrit 2.15+):",
            "Include a hashtag associated with all of the changes in the same group.",
            9
        )

        patchsetDescriptionTextField = addTextField(
            "Patch Set Description (Gerrit 3.4+):",
            "A description of the patch set to be created. Intended to help guide reviewers as a change evolves. The description cannot be changed after the change is pushed.",
            10
        )

        reviewersTextField = addTextField(
            "Reviewers (user names, comma separated):",
            "Users which will be added as reviewers.",
            11
        )

        ccTextField = addTextField(
            "CC (user names, comma separated):",
            "Users which will receive carbon copies of the notification message.",
            12
        )

        val settingLayoutPanel = JPanel()
        settingLayoutPanel.alignmentX = LEFT_ALIGNMENT
        settingLayoutPanel.layout = BoxLayout(settingLayoutPanel, BoxLayout.X_AXIS)
        settingLayoutPanel.add(Box.createRigidArea(Dimension(20, 0)))
        settingLayoutPanel.add(indentedSettingPanel)

        mainPanel.add(settingLayoutPanel)

        layout = BoxLayout(this, BoxLayout.X_AXIS)
        add(mainPanel)
        add(Box.createHorizontalGlue())
    }

    private fun addTextField(label: String, toolTipText: String, row: Int): JTextField {
        indentedSettingPanel!!.add(
            JLabel(label),
            GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        val textField = JTextField()
        textField.toolTipText = toolTipText
        indentedSettingPanel!!.add(
            textField,
            GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                Dimension(250, 0), null, null
            )
        )
        return textField
    }

    private fun addChangeListener() {
        val gerritPushChangeListener = ChangeActionListener()
        pushToGerritCheckBox!!.addActionListener(gerritPushChangeListener)
        privateCheckBox!!.addActionListener(gerritPushChangeListener)
        unmarkPrivateCheckBox!!.addActionListener(gerritPushChangeListener)
        wipCheckBox!!.addActionListener(gerritPushChangeListener)
        publishDraftCommentsCheckBox!!.addActionListener(gerritPushChangeListener)
        draftChangeCheckBox!!.addActionListener(gerritPushChangeListener)
        submitChangeCheckBox!!.addActionListener(gerritPushChangeListener)
        readyCheckBox!!.addActionListener(gerritPushChangeListener)

        val gerritPushTextChangeListener = ChangeTextActionListener()
        branchTextField!!.document.addDocumentListener(gerritPushTextChangeListener)
        topicTextField!!.document.addDocumentListener(gerritPushTextChangeListener)
        hashTagTextField!!.document.addDocumentListener(gerritPushTextChangeListener)
        patchsetDescriptionTextField!!.document.addDocumentListener(gerritPushTextChangeListener)
        reviewersTextField!!.document.addDocumentListener(gerritPushTextChangeListener)
        ccTextField!!.document.addDocumentListener(gerritPushTextChangeListener)
    }

    private val ref: String
        get() {
            var ref = "%s"
            if (pushToGerritCheckBox!!.isSelected) {
                ref = if (draftChangeCheckBox!!.isSelected) {
                    "refs/drafts/"
                } else {
                    "refs/for/"
                }
                ref += if (!branchTextField!!.text.isEmpty()) {
                    branchTextField!!.text
                } else {
                    "%s"
                }
                val gerritSpecs: MutableList<String?> = Lists.newArrayList()
                if (privateCheckBox!!.isSelected) {
                    gerritSpecs.add("private")
                } else if (unmarkPrivateCheckBox!!.isSelected) {
                    gerritSpecs.add("remove-private")
                }
                if (wipCheckBox!!.isSelected) {
                    gerritSpecs.add("wip")
                } else if (readyCheckBox!!.isSelected) {
                    gerritSpecs.add("ready")
                }
                if (publishDraftCommentsCheckBox!!.isSelected) {
                    gerritSpecs.add("publish-comments")
                }
                if (submitChangeCheckBox!!.isSelected) {
                    gerritSpecs.add("submit")
                }
                if (!topicTextField!!.text.isEmpty()) {
                    gerritSpecs.add("topic=" + topicTextField!!.text)
                }
                if (!hashTagTextField!!.text.isEmpty()) {
                    gerritSpecs.add("hashtag=" + hashTagTextField!!.text)
                }
                if (!patchsetDescriptionTextField!!.text.isEmpty()) {
                    gerritSpecs.add(
                        "m=" + UrlUtils.encodePatchSetDescription(
                            patchsetDescriptionTextField!!.text
                        )
                    )
                }
                handleCommaSeparatedUserNames(gerritSpecs, reviewersTextField, "r")
                handleCommaSeparatedUserNames(gerritSpecs, ccTextField, "cc")
                val gerritSpec = Joiner.on(',').join(gerritSpecs)
                if (!Strings.isNullOrEmpty(gerritSpec)) {
                    ref += "%%$gerritSpec"
                }
            }
            return ref
        }

    private fun handleExclusiveCheckBoxes() {
        privateCheckBox!!.isEnabled = !unmarkPrivateCheckBox!!.isSelected
        unmarkPrivateCheckBox!!.isEnabled = !privateCheckBox!!.isSelected
        wipCheckBox!!.isEnabled = !readyCheckBox!!.isSelected
        readyCheckBox!!.isEnabled = !wipCheckBox!!.isSelected
    }

    private fun handleCommaSeparatedUserNames(
        gerritSpecs: MutableList<String?>,
        textField: JTextField?,
        option: String
    ) {
        val items = COMMA_SPLITTER.split(
            textField!!.text
        )
        for (item in items) {
            gerritSpecs.add("$option=$item")
        }
    }

    private fun initDestinationBranch() {
        for ((key, value) in gerritPushTargetPanels) {
            key.initBranch(String.format(ref, value), pushToGerritByDefault)
        }
    }

    private fun updateDestinationBranch() {
        for ((key, value) in gerritPushTargetPanels) {
            key.updateBranch(String.format(ref, value))
        }
    }

    private fun setSettingsEnabled(enabled: Boolean) {
        UIUtil.setEnabled(indentedSettingPanel!!, enabled, true)
        if (enabled) {
            handleExclusiveCheckBoxes()
        }
    }

    /**
     * Updates destination branch text field after every config change.
     */
    private inner class ChangeActionListener : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            updateDestinationBranch()
            handleExclusiveCheckBoxes()
        }
    }

    /**
     * Updates destination branch text field after every text-field config change.
     */
    private inner class ChangeTextActionListener : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) {
            handleChange()
        }

        override fun removeUpdate(e: DocumentEvent) {
            handleChange()
        }

        override fun changedUpdate(e: DocumentEvent) {
            handleChange()
        }

        private fun handleChange() {
            updateDestinationBranch()
        }
    }

    /**
     * Activates or deactivates settings according to checkbox.
     */
    private inner class SettingsStateActionListener : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            setSettingsEnabled(pushToGerritCheckBox!!.isSelected)
        }
    }

    companion object {
        private val COMMA_SPLITTER: Splitter = Splitter.on(',').trimResults().omitEmptyStrings()
        private const val GITREVIEW_FILENAME = ".gitreview"
    }
}
