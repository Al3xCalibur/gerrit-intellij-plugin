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

import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.dvcs.push.VcsPushOptionsPanel
import git4idea.push.GitPushOptionsPanel
import git4idea.push.GitPushTagMode
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Wraps IntelliJ's GitPushOptionsPanel and Gerrit plugin push extension into one VcsPushOptionsPanel.
 *
 * @author Urs Wolfer
 */
class GerritPushOptionsPanel(pushToGerrit: Boolean, forceDefaultBranch: Boolean) : VcsPushOptionsPanel() {
    val gerritPushExtensionPanel: GerritPushExtensionPanel = GerritPushExtensionPanel(pushToGerrit, forceDefaultBranch)
    private var gitPushOptionsPanel: GitPushOptionsPanel? = null

    // javassist call
    fun initPanel(defaultMode: GitPushTagMode?, followTagsSupported: Boolean, showSkipHookOption: Boolean) {
        removeAll()
        gitPushOptionsPanel = GitPushOptionsPanel(defaultMode, followTagsSupported, showSkipHookOption)

        val mainContainer = JPanel()
        mainContainer.layout = BoxLayout(mainContainer, BoxLayout.PAGE_AXIS)

        mainContainer.add(gerritPushExtensionPanel)
        mainContainer.add(Box.createRigidArea(Dimension(0, 10)))
        mainContainer.add(gitPushOptionsPanel)

        add(mainContainer, BorderLayout.CENTER)

        gerritPushExtensionPanel.initialized()
    }

    override fun getValue(): VcsPushOptionValue? {
        return gitPushOptionsPanel!!.value
    }
}
