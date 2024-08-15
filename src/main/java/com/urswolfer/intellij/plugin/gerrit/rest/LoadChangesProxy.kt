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
package com.urswolfer.intellij.plugin.gerrit.rest

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.gerrit.extensions.api.changes.Changes
import com.google.gerrit.extensions.common.ChangeInfo
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * @author Thomas Forrer
 */
class LoadChangesProxy(
    private val queryRequest: Changes.QueryRequest,
    private val gerritUtil: GerritUtil,
    private val project: Project?
) {
    private var sortkey: String? = null
    private var hasMore = true
    private val changes: MutableList<ChangeInfo> = Lists.newArrayList()
    private val lock: Lock = ReentrantLock()

    /**
     * Load the next page of changes into the provided consumer
     */
    fun getNextPage(consumer: Consumer<List<ChangeInfo>>) {
        if (hasMore) {
            lock.lock()
            val myRequest = queryRequest.withLimit(PAGE_SIZE).withStart(changes.size)
            // remove sortkey handling once we drop Gerrit < 2.9 support
            if (sortkey != null) {
                myRequest.withSortkey(sortkey)
            }
            val myConsumer: Consumer<List<ChangeInfo>> = Consumer<List<ChangeInfo>> { changeInfos ->
                if (changeInfos != null && changeInfos.isNotEmpty()) {
                    val lastChangeInfo = changeInfos.last()
                    hasMore = lastChangeInfo._moreChanges != null && lastChangeInfo._moreChanges
                    sortkey = lastChangeInfo._sortkey
                    changes.addAll(changeInfos)
                } else {
                    hasMore = false
                }
                consumer.consume(changeInfos)
                lock.unlock()
            }
            gerritUtil.getChanges(myRequest, project, myConsumer)
        }
    }

    companion object {
        private const val PAGE_SIZE = 25
    }
}
