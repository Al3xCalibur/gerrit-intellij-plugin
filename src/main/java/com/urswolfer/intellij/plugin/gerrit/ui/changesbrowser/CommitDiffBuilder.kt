/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser

import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.VirtualFile
import com.urswolfer.intellij.plugin.gerrit.util.*
import git4idea.GitCommit
import git4idea.changes.GitChangeUtils

/**
 * This class diffs commits based in IntelliJ git4idea code and adds support for diffing commit msg.
 *
 * @author Thomas Forrer
 */
class CommitDiffBuilder(
    private val project: Project,
    private val gitRepositoryRoot: VirtualFile,
    private val base: GitCommit,
    private val commit: GitCommit
) {
    private var changesProvider: ChangesProvider = SimpleChangesProvider()

    fun withChangesProvider(changesProvider: ChangesProvider): CommitDiffBuilder {
        this.changesProvider = changesProvider
        return this
    }

    @get:Throws(VcsException::class)
    val diff: Collection<Change>
        get() {
            val baseHash = base.id.asString()
            val hash = commit.id.asString()
            val result = GitChangeUtils.getDiff(
                project, gitRepositoryRoot, baseHash, hash, null
            )
            result.add(buildCommitMsgChange())
            return result
        }

    private fun buildCommitMsgChange(): Change {
        val baseChange = Iterables.find(
            changesProvider.provide(
                base
            ), COMMIT_MSG_CHANGE_PREDICATE
        )
        val baseRevision = baseChange.afterRevision
        val change = Iterables.find(
            changesProvider.provide(
                commit
            ), COMMIT_MSG_CHANGE_PREDICATE
        )
        val revision = change.afterRevision
        return Change(baseRevision, revision)
    }

    interface ChangesProvider {
        fun provide(gitCommit: GitCommit): Collection<Change>
    }

    private class SimpleChangesProvider : ChangesProvider {
        override fun provide(gitCommit: GitCommit): Collection<Change> {
            return gitCommit.changes
        }
    }

    companion object {
        private val COMMIT_MSG_CHANGE_PREDICATE = Predicate<Change> { change: Change? ->
            val commitMsgFile = "/COMMIT_MSG"
            val afterRevision = change!!.afterRevision
            if (afterRevision != null) {
                return@Predicate commitMsgFile == PathUtils.ensureSlashSeparators(afterRevision.file.path)
            }
            val beforeRevision = change.beforeRevision
            if (beforeRevision != null) {
                return@Predicate commitMsgFile == PathUtils.ensureSlashSeparators(beforeRevision.file.path)
            }
            throw IllegalStateException("Change should have at least one ContentRevision set.")
        }
    }
}
