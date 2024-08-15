/*
 * Copyright 2013-2015 Urs Wolfer
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

import com.google.common.base.Strings
import com.google.gerrit.extensions.api.GerritApi
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.SuggestedReviewerInfo
import com.google.gerrit.extensions.restapi.RestApiException
import com.google.inject.Inject
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextField
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.SoftWrapsEditorCustomization
import com.intellij.util.TextFieldCompletionProviderDumbAware
import com.urswolfer.gerrit.client.rest.GerritRestApi
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import java.awt.Dimension
import javax.swing.JComponent

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class AddReviewersAction :
    AbstractLoggedInChangeAction("Add Reviewers", "Add Reviewers to Change", AllIcons.Toolwindows.ToolWindowTodo) {
    @Inject
    private lateinit var gerritApi: GerritRestApi

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val selectedChange = getSelectedChange(anActionEvent) ?: return

        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)!!
        val dialog = AddReviewersDialog(project, true, gerritApi, selectedChange)
        dialog.show()
        if (!dialog.isOK) {
            return
        }
        val content = dialog.reviewTextField.text
        val reviewerNames = content.splitToSequence(",").filter(String::isNotEmpty).map(String::trim)
        for (reviewerName in reviewerNames) {
            gerritUtil.addReviewer(selectedChange.id, reviewerName, project)
        }
    }

    private class AddReviewersDialog(
        project: Project,
        canBeParent: Boolean,
        gerritApi: GerritApi,
        changeInfo: ChangeInfo?
    ) : DialogWrapper(project, canBeParent) {
        val reviewTextField: EditorTextField

        init {
            title = "Add Reviewers to Change"
            setOKButtonText("Add Reviewers")

            val service = ApplicationManager.getApplication().getService(
                EditorTextFieldProvider::class.java
            )
            val editorFeatures: MutableSet<EditorCustomization?> = HashSet()
            editorFeatures.add(SoftWrapsEditorCustomization.ENABLED)
            editorFeatures.add(SpellCheckingEditorCustomizationProvider.getInstance().disabledCustomization)
            reviewTextField = service.getEditorField(FileTypes.PLAIN_TEXT.language, project, editorFeatures)
            reviewTextField.minimumSize = Dimension(500, 100)
            buildTextFieldCompletion(gerritApi, changeInfo)

            init()
        }

        private fun buildTextFieldCompletion(gerritApi: GerritApi, changeInfo: ChangeInfo?) {
            val completionProvider: TextFieldCompletionProviderDumbAware =
                object : TextFieldCompletionProviderDumbAware(true) {
                    override fun getPrefix(currentTextPrefix: String): String {
                        val text = currentTextPrefix.lastIndexOf(',')
                        return if (text == -1) currentTextPrefix else currentTextPrefix.substring(text + 1)
                            .trim { it <= ' ' }
                    }

                    override fun addCompletionVariants(
                        text: String,
                        offset: Int,
                        prefix: String,
                        result: CompletionResultSet
                    ) {
                        if (Strings.isNullOrEmpty(prefix)) {
                            return
                        }
                        try {
                            val suggestedReviewers = gerritApi.changes()
                                .id(changeInfo!!._number).suggestReviewers(prefix).withLimit(20).get()
                            if (result.isStopped) {
                                return
                            }
                            for (suggestedReviewer in suggestedReviewers) {
                                val lookupElementBuilderOptional = buildLookupElement(suggestedReviewer)
                                if (lookupElementBuilderOptional != null) {
                                    result.addElement(lookupElementBuilderOptional)
                                }
                            }
                        } catch (e: RestApiException) {
                            throw RuntimeException(e)
                        }
                    }
                }
            completionProvider.apply(reviewTextField)
        }

        private fun buildLookupElement(suggestedReviewer: SuggestedReviewerInfo): LookupElementBuilder? {
            val presentableText: String
            val reviewerName: String
            if (suggestedReviewer.account != null) {
                val account = suggestedReviewer.account
                presentableText = if (account.email != null) {
                    "${account.name} <${account.email}>"
                } else {
                    "${account.name} (${account._accountId})"
                }
                reviewerName = presentableText
            } else if (suggestedReviewer.group != null) {
                presentableText = "${suggestedReviewer.group.name} (group)"
                reviewerName = suggestedReviewer.group.name
            } else {
                return null
            }
            return LookupElementBuilder.create("$reviewerName,").withPresentableText(presentableText)
        }

        override fun createCenterPanel(): JComponent {
            return reviewTextField
        }

        override fun getPreferredFocusedComponent(): JComponent {
            return reviewTextField
        }
    }

    class Proxy : AddReviewersAction() {
        private val delegate: AddReviewersAction = GerritModule.getInstance<AddReviewersAction>()

        override fun update(e: AnActionEvent) {
            delegate.update(e)
        }

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
