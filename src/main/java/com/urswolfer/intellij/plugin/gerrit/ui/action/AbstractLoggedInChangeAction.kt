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
package com.urswolfer.intellij.plugin.gerrit.ui.action

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import javax.swing.Icon

/**
 * Actions which require a logged in user need to extend this class.
 * Important: if a proxy is used for Guice, make sure that your delegate the #update method.
 *
 * @author Urs Wolfer
 */
abstract class AbstractLoggedInChangeAction(text: String?, description: String?, icon: Icon?) :
    AbstractChangeAction(text, description, icon) {
    @Inject
    protected lateinit var gerritSettings: GerritSettings

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = gerritSettings.isLoginAndPasswordAvailable
    }
}
