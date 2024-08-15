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
package com.urswolfer.intellij.plugin.gerrit.ui.action

import com.google.inject.Inject
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService
import javax.swing.Icon

/**
 * @author Thomas Forrer
 */
class ReviewActionFactory @Inject constructor(
    private val gerritUtil: GerritUtil,
    private val gerritSettings: GerritSettings,
    private val submitAction: SubmitAction,
    private val selectedRevisions: SelectedRevisions,
    private val notificationService: NotificationService
) {
    fun get(label: String, rating: Int, icon: Icon?, showDialog: Boolean): ReviewAction {
        return ReviewAction(
            label, rating, icon, showDialog,
            selectedRevisions, gerritUtil, submitAction, notificationService, gerritSettings
        )
    }
}
