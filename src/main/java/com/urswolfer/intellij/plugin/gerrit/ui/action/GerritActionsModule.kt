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

import com.google.inject.AbstractModule

/**
 * @author Thomas Forrer
 */
class GerritActionsModule : AbstractModule() {
    override fun configure() {
        bind(ReviewActionFactory::class.java)

        bind(AddReviewersAction::class.java)
        bind(FetchAction::class.java)
        bind(CheckoutAction::class.java)
        bind(CherryPickAction::class.java)
        bind(CompareBranchAction::class.java)
        bind(OpenInBrowserAction::class.java)
        bind(SettingsAction::class.java)
        bind(SubmitAction::class.java)
        bind(AbandonAction::class.java)
        bind(RefreshAction::class.java)
        bind(StarAction::class.java)
    }
}
