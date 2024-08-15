/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ClickListener
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import java.awt.event.*
import javax.swing.*
import javax.swing.border.Border

/**
 * Merge of original BasePopupAction (IntelliJ < 14) and com.intellij.vcs.log.ui.filter.FilterPopupComponent.
 */
abstract class BasePopupAction(filterName: String) : DumbAwareAction(), CustomComponentAction, UpdateInBackground {
    private val myFilterNameLabel = JLabel("$filterName: ")
    protected val filterValueLabel: JLabel = JLabel()
    private val myPanel = JPanel()

    init {
        val layout = BoxLayout(myPanel, BoxLayout.X_AXIS)
        myPanel.layout = layout
        myPanel.isFocusable = true
        myPanel.border = UNFOCUSED_BORDER

        myPanel.add(myFilterNameLabel)
        myPanel.add(filterValueLabel)
        myPanel.add(Box.createHorizontalStrut(GAP_BEFORE_ARROW))
        myPanel.add(JLabel(AllIcons.Ide.Statusbar_arrows))

        showPopupMenuOnClick()
        showPopupMenuFromKeyboard()
        indicateHovering()
        indicateFocusing()
    }

    private fun createActionGroup(): DefaultActionGroup {
        val group = DefaultActionGroup()
        createActions { anAction -> group.add(anAction!!) }
        return group
    }

    protected abstract fun createActions(actionConsumer: Consumer<AnAction?>)

    override fun actionPerformed(e: AnActionEvent) {
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return myPanel
    }

    protected fun updateFilterValueLabel(text: String?) {
        filterValueLabel.text = text
    }

    private fun indicateFocusing() {
        myPanel.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                myPanel.border = FOCUSED_BORDER
            }

            override fun focusLost(e: FocusEvent) {
                myPanel.border = UNFOCUSED_BORDER
            }
        })
    }

    private fun showPopupMenuFromKeyboard() {
        myPanel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_DOWN) {
                    showPopupMenu()
                }
            }
        })
    }

    private fun showPopupMenuOnClick() {
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                showPopupMenu()
                return true
            }
        }.installOn(myPanel)
    }

    private fun indicateHovering() {
        myPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                setOnHoverForeground()
            }

            override fun mouseExited(e: MouseEvent) {
                setDefaultForeground()
            }
        })
    }

    private fun setDefaultForeground() {
        myFilterNameLabel.foreground = UIUtil.getLabelForeground()
        filterValueLabel.foreground = UIUtil.getLabelForeground()
    }

    private fun setOnHoverForeground() {
        myFilterNameLabel.foreground = UIUtil.getLabelForeground()
        filterValueLabel.foreground = UIUtil.getLabelForeground()
    }


    private fun showPopupMenu() {
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            null, createActionGroup(),
            DataManager.getInstance().getDataContext(myPanel), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false
        )
        popup.showUnderneathOf(myPanel)
    }

    companion object {
        private const val GAP_BEFORE_ARROW = 3
        private const val BORDER_SIZE = 2
        private val INNER_MARGIN_BORDER: Border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
        private val FOCUSED_BORDER = createFocusedBorder()
        private val UNFOCUSED_BORDER = createUnfocusedBorder()

        private fun createFocusedBorder(): Border {
            return BorderFactory.createCompoundBorder(
                RoundedLineBorder(UIUtil.getHeaderActiveColor(), 10, BORDER_SIZE),
                INNER_MARGIN_BORDER
            )
        }

        private fun createUnfocusedBorder(): Border {
            return BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE),
                INNER_MARGIN_BORDER
            )
        }
    }
}
