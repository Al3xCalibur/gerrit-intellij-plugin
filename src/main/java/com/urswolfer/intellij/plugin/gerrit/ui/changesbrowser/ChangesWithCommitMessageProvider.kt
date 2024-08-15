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
package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.RemoteFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.SimpleContentRevision
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.CommitDiffBuilder.ChangesProvider
import git4idea.GitCommit

/**
 * @author Thomas Forrer
 */
class ChangesWithCommitMessageProvider : ChangesProvider {
    override fun provide(gitCommit: GitCommit): Collection<Change> {
        return getChangesWithCommitMessage(gitCommit)
    }

    private fun getChangesWithCommitMessage(gitCommit: GitCommit): Collection<Change> {
        val changes = gitCommit.changes

        val content = CommitMessageFormatter(gitCommit).longCommitMessage
        val commitMsg: FilePath = object : RemoteFilePath("/COMMIT_MSG", false) {
            override fun getFileType(): FileType {
                return PlainTextFileType.INSTANCE
            }
        }

        changes.add(
            Change(
                null, SimpleContentRevision(
                    content,
                    commitMsg,
                    gitCommit.id.asString()
                )
            )
        )
        return changes
    }
}
