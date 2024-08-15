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
package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser

import com.google.common.base.Joiner
import com.google.common.collect.Iterables
import git4idea.GitCommit
import java.text.SimpleDateFormat
import java.util.*

/**
 * This class formats the commit message as similarly as possible to how Gerrit formats it.
 * It mainly needs to have the same content on the same line, in order for comments to be displayed and posted correctly.
 * The subject line for parent commit(s) is missing, to avoid fetching more commits from git at this stage. It might be
 * implemented later.
 *
 * The main reason for this class to be necessary is the fact that the REST endpoint to retrieve a file's content does
 * not support requesting the commit message's content. It could be constructed using the diff endpoint, but this seemed
 * more complex and would result in unnecessary REST calls.
 *
 * However, this class might be re-implemented at a later time.
 *
 * @author Thomas Forrer
 */
class CommitMessageFormatter(private val gitCommit: GitCommit) {
    val longCommitMessage: String
        get() = String.format(
            PATTERN,
            parentLine,
            gitCommit.author.name, gitCommit.author.email,
            DATE_FORMAT.get().format(Date(gitCommit.authorTime)),
            gitCommit.committer.name, gitCommit.committer.email,
            DATE_FORMAT.get().format(gitCommit.commitTime),
            gitCommit.fullMessage
        )

    private val parentLine: String
        get() {
            val parents = gitCommit.parents
            if (parents.size == 1) {
                val parent = Iterables.getOnlyElement(parents)
                return String.format(PARENT_PATTERN, parent!!.asString())
            } else if (parents.size > 1) {
                val allParents = Joiner.on(MERGE_PATTERN_DELIMITER).join(parents)
                return String.format(MERGE_PATTERN, allParents)
            } else {
                return ""
            }
        }

    companion object {
        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss Z"

        private val DATE_FORMAT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                val dateFormat = SimpleDateFormat(DATE_PATTERN)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                return dateFormat
            }
        }
        private const val PARENT_PATTERN = "Parent:     %s\n"
        private const val MERGE_PATTERN = "Merge Of:   %s\n"
        private const val MERGE_PATTERN_DELIMITER = "\n            "
        private const val PATTERN = "%s" +
                "Author:     %s <%s>\n" +
                "AuthorDate: %s\n" +
                "Commit:     %s <%s>\n" +
                "CommitDate: %s\n" +
                "\n" +
                "%s\n"
    }
}
