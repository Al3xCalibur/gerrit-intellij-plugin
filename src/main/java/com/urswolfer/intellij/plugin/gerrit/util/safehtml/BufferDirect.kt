// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.urswolfer.intellij.plugin.gerrit.util.safehtml

// based on: https://gerrit.googlesource.com/gerrit/+/master/gerrit-gwtexpui/src/main/java/com/google/gwtexpui/safehtml/client/

internal class BufferDirect : Buffer {
    private val strbuf = StringBuilder()

    val isEmpty: Boolean
        get() = strbuf.length == 0

    override fun append(v: Boolean) {
        strbuf.append(v)
    }

    override fun append(v: Char) {
        strbuf.append(v)
    }

    override fun append(v: Int) {
        strbuf.append(v)
    }

    override fun append(v: Long) {
        strbuf.append(v)
    }

    override fun append(v: Float) {
        strbuf.append(v)
    }

    override fun append(v: Double) {
        strbuf.append(v)
    }

    override fun append(v: String?) {
        strbuf.append(v)
    }

    override fun toString(): String {
        return strbuf.toString()
    }
}
