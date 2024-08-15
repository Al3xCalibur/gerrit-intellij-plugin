/*
 * Copyright 2013-2014 Urs Wolfer
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.google.common.collect.*
import com.google.gerrit.extensions.common.*
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.UIVcsUtil
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.text.DecimalFormat
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent


/**
 * Parts based on:
 * git4idea.history.wholeTree.GitLogDetailsPanel
 *
 * @author Urs Wolfer
 */
class GerritChangeDetailsPanel(project: Project) {
    val component: JPanel = JPanel(CardLayout())

    private val presentationData: MyPresentationData

    private val jEditorPane: JEditorPane

    init {
        component.add(UIVcsUtil.errorPanel("Nothing selected", false), NOTHING_SELECTED)
        component.add(UIVcsUtil.errorPanel("Loading...", false), LOADING)

        presentationData = MyPresentationData(project)

        val wrapper = JPanel(BorderLayout())

        // could be ported to com.intellij.util.ui.HtmlPanel once minimal IntelliJ version is bumped
        jEditorPane = JEditorPane(UIUtil.HTML_MIME, "")
        jEditorPane.preferredSize = Dimension(150, 100)
        jEditorPane.isEditable = false
        jEditorPane.isOpaque = false
        jEditorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        jEditorPane.addHyperlinkListener { e -> this@GerritChangeDetailsPanel.handleHyperlinkEvent(e) }

        val tableScroll = JBScrollPane(jEditorPane)
        tableScroll.border = null
        wrapper.add(tableScroll, SwingConstants.CENTER)

        component.add(wrapper, DATA)
        nothingSelected()
    }

    fun nothingSelected() {
        (component.layout as CardLayout).show(component, NOTHING_SELECTED)
    }


    fun loading() {
        (component.layout as CardLayout).show(component, LOADING)
    }

    fun setData(changeInfo: ChangeInfo) {
        presentationData.setCommit(changeInfo)
        (component.layout as CardLayout).show(component, DATA)

        changeDetailsText()
    }

    private fun handleHyperlinkEvent(e: HyperlinkEvent) {
        if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            BrowserUtil.browse(e.url)
        }
    }

    private fun changeDetailsText() {
        if (presentationData.isReady) {
            jEditorPane.text = presentationData.text
            component.revalidate()
            component.repaint()
            jEditorPane.caretPosition = 0
        }
    }

    private class MyPresentationData(private val project: Project) {
        private var startPattern: String? = null

        fun setCommit(changeInfo: ChangeInfo) {
            val stringBuilder = StringBuilder()
            addMetaData(changeInfo, stringBuilder)
            addLabels(changeInfo, stringBuilder)
            addMessages(changeInfo, stringBuilder)
            startPattern = stringBuilder.toString()
        }

        private fun addMetaData(changeInfo: ChangeInfo, sb: StringBuilder) {
            val comment = if (changeInfo.subject != null) IssueLinkHtmlRenderer.formatTextWithLinks(
                project,
                changeInfo.subject
            ) else "-"
            sb.append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
                .append("</head><body><table>")
                .append("<tr valign=\"top\"><td><i>Change-Id:</i></td><td><b>").append(changeInfo.changeId)
                .append("</b></td></tr>")
                .append("<tr valign=\"top\"><td><i>Change #:</i></td><td><b>").append(changeInfo._number)
                .append("</b></td></tr>")
                .append("<tr valign=\"top\"><td><i>Owner:</i></td><td>")
                .append(if (changeInfo.owner != null) changeInfo.owner.name else "").append("</td></tr>")
                .append("<tr valign=\"top\"><td><i>Project:</i></td><td>").append(changeInfo.project)
                .append("</td></tr>")
                .append("<tr valign=\"top\"><td><i>Branch:</i></td><td>").append(changeInfo.branch).append("</td></tr>")
                .append("<tr valign=\"top\"><td><i>Topic:</i></td><td>")
                .append(if (changeInfo.topic != null) changeInfo.topic else "").append("</td></tr>")
                .append("<tr valign=\"top\"><td><i>Uploaded:</i></td><td>")
                .append(if (changeInfo.created != null) DateFormatUtil.formatPrettyDateTime(changeInfo.created) else "")
                .append("</td></tr>")
                .append("<tr valign=\"top\"><td><i>Updated:</i></td><td>")
                .append(if (changeInfo.updated != null) DateFormatUtil.formatPrettyDateTime(changeInfo.updated) else "")
                .append("</td></tr>")
                .append("<tr valign=\"top\"><td><i>Status:</i></td><td>").append(changeInfo.status).append("</td></tr>")
                .append("<tr valign=\"top\"><td><i>Description:</i></td><td><b>").append(comment)
                .append("</b></td></tr>")
        }

        private fun addLabels(changeInfo: ChangeInfo, sb: StringBuilder) {
            if (changeInfo.labels != null) {
                var ccAccounts: MutableList<ApprovalInfo>? = null
                for ((key, value) in changeInfo.labels) {
                    sb.append("<tr valign=\"top\"><td><i>").append(key).append(":</i></td><td>")
                    val all = value.all
                    if (ccAccounts == null) {
                        ccAccounts = if (all != null) {
                            Lists.newArrayList(all)
                        } else {
                            Lists.newArrayList()
                        }
                    }
                    if (all != null) {
                        for (approvalInfo in all) {
                            if (approvalInfo.value != null && approvalInfo.value != 0) {
                                sb.append("<b>").append(approvalInfo.name).append("</b>").append(": ")
                                sb.append(APPROVAL_VALUE_FORMAT.get().format(approvalInfo.value)).append("<br/>")
                                ccAccounts!!.remove(approvalInfo) // remove accounts from CC which are already listed in a review section
                            }
                        }
                    }
                    sb.append("</td></tr>")
                }
                if (ccAccounts != null) {
                    sb.append("<tr valign=\"top\"><td><i>").append("CC").append(":</i></td><td>")
                    for (approvalInfo in ccAccounts) {
                        sb.append("<b>").append(approvalInfo.name).append("</b>").append("<br/>")
                    }
                }
            }
        }

        private fun addMessages(changeInfo: ChangeInfo, sb: StringBuilder) {
            if (changeInfo.messages != null && !changeInfo.messages.isEmpty()) {
                sb.append("<tr valign=\"top\"><td><i>Comments:</i></td><td>")
                for (changeMessageInfo in changeInfo.messages) {
                    val author = changeMessageInfo.author
                    if (author?.name != null) {
                        sb.append("<b>").append(author.name).append("</b>")
                        if (changeMessageInfo.date != null) {
                            sb.append(" (").append(DateFormatUtil.formatPrettyDateTime(changeMessageInfo.date))
                                .append(')')
                        }
                        sb.append(": ")
                    }
                    sb.append(TextToHtml.textToHtml(changeMessageInfo.message)).append("<br/>")
                }
                sb.append("</td></tr>")
            }
        }

        val isReady: Boolean
            get() = startPattern != null

        val text: String
            get() = startPattern + endPattern

        companion object {
            private const val endPattern = "</table></body></html>"
        }
    }

    companion object {
        private const val NOTHING_SELECTED = "nothingSelected"
        private const val LOADING = "loading"
        private const val DATA = "data"
        private val APPROVAL_VALUE_FORMAT: ThreadLocal<DecimalFormat> = object : ThreadLocal<DecimalFormat>() {
            override fun initialValue(): DecimalFormat {
                return DecimalFormat("+#;-#")
            }
        }
    }
}
