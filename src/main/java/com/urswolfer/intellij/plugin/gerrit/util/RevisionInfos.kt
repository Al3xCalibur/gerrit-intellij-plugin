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
package com.urswolfer.intellij.plugin.gerrit.util

import com.google.common.primitives.Ints
import com.google.gerrit.extensions.common.RevisionInfo

/**
 * @author Thomas Forrer
 */
object RevisionInfos {
    val MAP_ENTRY_COMPARATOR: Comparator<Map.Entry<String?, RevisionInfo>> =
        Comparator { o1: Map.Entry<String?, RevisionInfo>, o2: Map.Entry<String?, RevisionInfo> ->
            compare(
                o1.value,
                o2.value
            )
        }

    fun compare(r1: RevisionInfo, r2: RevisionInfo): Int {
        return Ints.compare(r1._number, r2._number)
    }
}
