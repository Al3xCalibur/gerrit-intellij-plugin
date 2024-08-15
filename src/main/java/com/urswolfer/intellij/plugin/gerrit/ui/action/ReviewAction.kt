/*
 * Copyright 2013-2016 Urs Wolfer
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

import com.google.common.base.Joiner
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.gerrit.extensions.api.changes.NotifyHandling
import com.google.gerrit.extensions.api.changes.ReviewInput
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.gerrit.extensions.common.CommentInfo
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewDialog
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService
import javax.swing.Icon

/**
 * @author Urs Wolfer
 */
// needs to be setup with correct parameters (ctor); see corresponding factory
class ReviewAction(
    private val label: String,
    private val rating: Int,
    icon: Icon?,
    private val showDialog: Boolean,
    private val selectedRevisions: SelectedRevisions,
    gerritUtil: GerritUtil,
    private val submitAction: SubmitAction,
    private val notificationService: NotificationService,
    gerritSettings: GerritSettings
) : AbstractLoggedInChangeAction(
    (if (rating > 0) "+" else "") + rating + (if (showDialog) "..." else ""),
    "Review Change with " + rating + (if (showDialog) " adding Comment" else ""),
    icon
) {
    init {
        this.gerritSettings = gerritSettings
        this.gerritUtil = gerritUtil
    }

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)

        val changeDetails = getSelectedChange(anActionEvent) ?: return

        gerritUtil.getComments(
            changeDetails._number, selectedRevisions[changeDetails], project, false, true
        ) { draftComments: Map<String, List<CommentInfo>> ->
            val reviewInput = ReviewInput()
            reviewInput.label(label, rating)

            for ((key, value) in draftComments) {
                for (commentInfo in value) {
                    addComment(reviewInput, key, commentInfo)
                }
            }

            var submitChange = false
            if (showDialog) {
                val dialog = ReviewDialog(project)
                dialog.show()
                if (!dialog.isOK) {
                    return@getComments
                }
                val message = dialog.reviewPanel.message
                if (!Strings.isNullOrEmpty(message)) {
                    reviewInput.message = message
                }
                submitChange = dialog.reviewPanel.submitChange

                if (!dialog.reviewPanel.doNotify) {
                    reviewInput.notify = NotifyHandling.NONE
                }
            }

            val finalSubmitChange = submitChange
            gerritUtil.postReview(
                changeDetails.id,
                selectedRevisions[changeDetails],
                reviewInput,
                project
            ) {
                val notification = NotificationBuilder(
                    project, "Review posted",
                    buildSuccessMessage(changeDetails, reviewInput)
                )
                    .hideBalloon()
                notificationService.notifyInformation(notification)
                if (finalSubmitChange) {
                    submitAction.actionPerformed(anActionEvent)
                }
            }
        }
    }

    private fun addComment(reviewInput: ReviewInput, path: String, comment: CommentInfo) {
        val commentInputs: MutableList<CommentInput?>
        var comments = reviewInput.comments
        if (comments == null) {
            comments = Maps.newHashMap()
            reviewInput.comments = comments
        }
        if (comments.containsKey(path)) {
            commentInputs = comments[path]!!
        } else {
            commentInputs = Lists.newArrayList()
            comments[path] = commentInputs
        }

        val commentInput = CommentInput()
        commentInput.id = comment.id
        commentInput.path = comment.path
        commentInput.side = comment.side
        commentInput.line = comment.line
        commentInput.range = comment.range
        commentInput.inReplyTo = comment.inReplyTo
        commentInput.updated = comment.updated
        commentInput.message = comment.message

        commentInputs.add(commentInput)
    }

    private fun buildSuccessMessage(changeInfo: ChangeInfo, reviewInput: ReviewInput): String {
        val stringBuilder = StringBuilder(
            String.format("Review for change '%s' posted", changeInfo.subject)
        )
        if (reviewInput.labels.isNotEmpty()) {
            stringBuilder.append(": ")
            stringBuilder.append(Joiner.on(", ").withKeyValueSeparator(": ").join(reviewInput.labels))
        }
        return stringBuilder.toString()
    }
}
