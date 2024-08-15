package com.urswolfer.intellij.plugin.gerrit.ui.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.urswolfer.intellij.plugin.gerrit.GerritModule

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class StarAction : AbstractLoggedInChangeAction("Star", "Switch star status of change", AllIcons.Nodes.Favorite) {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val changeInfo = getSelectedChange(anActionEvent) ?: return

        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)
        gerritUtil.changeStarredStatus(changeInfo.id, !(changeInfo.starred != null && changeInfo.starred), project)
    }

    class Proxy : StarAction() {
        private val delegate: StarAction = GerritModule.getInstance<StarAction>()

        override fun update(e: AnActionEvent) {
            delegate.update(e)
        }

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
