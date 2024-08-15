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

import com.google.common.base.Optional
import com.google.common.collect.*
import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Comparing
import com.intellij.util.Consumer
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction
import java.awt.Point
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * @author Thomas Forrer
 */
abstract class AbstractUserFilter : AbstractChangesFilter() {
    @Inject
    private lateinit var jbPopupFactory: JBPopupFactory

    private var users: List<User>? = null
    private lateinit var popup: JBPopup
    private var selectOkAction: AnAction? = null
    private lateinit var selectUserTextArea: JTextArea
    private var value: User? = null

    abstract val actionLabel: String
    abstract val queryField: String

    override fun getAction(project: Project?): AnAction {
        users = listOf(
            User("All", null),
            User("Me", "self")
        )
        value = users!![0]
        return UserPopupAction(project, actionLabel)
    }

    override val searchQueryPart: String?
        get() {
            val value = value
            if (value != null && value.forQuery.isPresent) {
                var queryValue = value.forQuery.get()
                queryValue = FulltextFilter.specialEncodeFulltextQuery(queryValue)
                return "$queryField:$queryValue"
            } else {
                return null
            }
        }

    private class User(var label: String, forQuery: String?) {
        var forQuery: Optional<String> = Optional.fromNullable(forQuery)
    }

    inner class UserPopupAction(private val project: Project?, labelText: String) : BasePopupAction(labelText) {
        init {
            updateFilterValueLabel(value!!.label)
        }

        override fun createActions(actionConsumer: Consumer<AnAction?>) {
            for (user in users!!) {
                actionConsumer.consume(object : DumbAwareAction(user.label) {
                    override fun actionPerformed(e: AnActionEvent) {
                        change(user)
                    }
                })
            }
            selectUserTextArea = JTextArea()
            selectOkAction = buildOkAction()
            actionConsumer.consume(object : DumbAwareAction("Select...") {
                override fun actionPerformed(e: AnActionEvent) {
                    popup = buildBalloon(selectUserTextArea)
                    val point = Point(0, 0)
                    SwingUtilities.convertPointToScreen(point, filterValueLabel)
                    popup.showInScreenCoordinates(filterValueLabel, point)
                    val content = popup.content
                    selectOkAction!!.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, content)
                    popup.addListener(object : JBPopupListener {
                        override fun beforeShown(lightweightWindowEvent: LightweightWindowEvent) {}

                        override fun onClosed(event: LightweightWindowEvent) {
                            selectOkAction!!.unregisterCustomShortcutSet(content)
                        }
                    })
                }
            })
        }

        private fun change(user: User) {
            value = user
            updateFilterValueLabel(user.label)
            setChanged()
            notifyObservers(project)
        }

        private fun buildOkAction(): AnAction {
            return object : AnAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup.closeOk(e.inputEvent)
                    val newText = selectUserTextArea.text.trim { it <= ' ' }
                    if (newText.isEmpty()) {
                        return
                    }
                    if (!Comparing.equal(newText, filterValueLabel.text, true)) {
                        val user = User(newText, newText)
                        change(user)
                    }
                }
            }
        }

        private fun buildBalloon(textArea: JTextArea): JBPopup {
            val builder = jbPopupFactory.createComponentPopupBuilder(textArea, textArea)
            builder.setAdText(POPUP_TEXT)
            builder.setResizable(true)
            builder.setMovable(true)
            builder.setRequestFocus(true)
            return builder.createPopup()
        }
    }

    companion object {
        private val POPUP_TEXT = "${KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.shortcuts)} to search"
    }
}
