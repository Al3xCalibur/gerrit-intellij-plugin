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

import com.google.gerrit.extensions.common.ChangeInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.ui.SimpleColoredComponent

/**
 * Interface for node decorators in this plugin's
 * [com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser].
 *
 * Implementations might be added to the corresponding [com.google.inject.multibindings.Multibinder] in
 * [com.urswolfer.intellij.plugin.gerrit.ui.GerritUiModule].
 *
 * @author Thomas Forrer
 */
interface GerritChangeNodeDecorator {
    /**
     * Decorate the `component` on the provided `change` in the provided `project`
     */
    fun decorate(project: Project, change: Change, component: SimpleColoredComponent, selectedChange: ChangeInfo?)

    /**
     * This method is called, when a new change is selected in the changes list panel
     */
    fun onChangeSelected(project: Project?, selectedChange: ChangeInfo?)
}
