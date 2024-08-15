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
package com.urswolfer.intellij.plugin.gerrit

import com.google.gerrit.extensions.common.ChangeInfo
import java.util.*

/**
 * Class keeping record of all selected revisions by change.
 *
 * @author Thomas Forrer
 */
class SelectedRevisions : Observable() {
    private val map: MutableMap<String, String?> = mutableMapOf()

    /**
     * @return the selected revision for the provided changeId, or null if the current revision was selected.
     */
    operator fun get(changeId: String): String? {
        return map[changeId]
    }

    /**
     * @return the selected revision for the provided change info object
     */
    operator fun get(changeInfo: ChangeInfo): String? {
        var currentRevision = changeInfo.currentRevision
        if (currentRevision == null && changeInfo.revisions != null) {
            // don't know why with some changes currentRevision is not set,
            // the revisions map however is usually populated
            val revisionKeys: Set<String> = changeInfo.revisions.keys
            if (revisionKeys.isNotEmpty()) {
                currentRevision = revisionKeys.last()
            }
        }
        return get(changeInfo.id) ?: currentRevision
    }

    operator fun set(changeId: String, revisionHash: String?) {
        map[changeId] = revisionHash
        setChanged()
        notifyObservers(changeId)
    }

    fun clear() {
        map.clear()
        setChanged()
        notifyObservers()
    }
}
