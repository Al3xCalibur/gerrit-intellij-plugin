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

import java.util.*

// based on: https://gerrit.googlesource.com/gerrit/+/master/gerrit-gwtexpui/src/main/java/com/google/gwtexpui/safehtml/client/

/** Lightweight map of names/values for element attribute construction.  */
internal class AttMap {
    private val names = ArrayList<String>()
    private val values = ArrayList<String>()

    private var tag: Tag = ANY
    private var live = 0

    fun reset(tagName: String) {
        tag = TAGS[tagName.lowercase(Locale.getDefault())] ?: ANY
        live = 0
    }

    fun onto(raw: Buffer, esc: SafeHtmlBuilder) {
        for (i in 0 until live) {
            val v = values[i]
            if (v.isNotEmpty()) {
                raw.append(" ")
                raw.append(names[i])
                raw.append("=\"")
                esc.append(v)
                raw.append("\"")
            }
        }
    }

    fun get(name: String): String {
        val name = name.lowercase(Locale.getDefault())
        for (i in 0 until live) {
            if (name == names[i]) {
                return values[i]
            }
        }
        return ""
    }

    fun set(name: String, value: String) {
        val name = name.lowercase(Locale.getDefault())
        tag.assertSafe(name, value)

        for (i in 0 until live) {
            if (name == names[i]) {
                values[i] = value
                return
            }
        }

        val i = live++
        if (names.size < live) {
            names.add(name)
            values.add(value)
        } else {
            names[i] = name
            values[i] = value
        }
    }

    private interface Tag {
        fun assertSafe(name: String, value: String)
    }

    private class AnyTag : Tag {
        override fun assertSafe(name: String, value: String) {}
    }

    private class AnchorTag : Tag {
        override fun assertSafe(name: String, value: String) {
            if ("href" == name) {
                assertNotJavascriptUrl(value)
            }
        }
    }

    private class FormTag : Tag {
        override fun assertSafe(name: String, value: String) {
            if ("action" == name) {
                assertNotJavascriptUrl(value)
            }
        }
    }

    private class SrcTag : Tag {
        override fun assertSafe(name: String, value: String) {
            if ("src" == name) {
                assertNotJavascriptUrl(value)
            }
        }
    }

    companion object {
        private val ANY: Tag = AnyTag()
        private val TAGS: HashMap<String, Tag>

        init {
            val src: Tag = SrcTag()
            TAGS = HashMap()
            TAGS["a"] = AnchorTag()
            TAGS["form"] = FormTag()
            TAGS["img"] = src
            TAGS["script"] = src
            TAGS["frame"] = src
        }

        private fun assertNotJavascriptUrl(value: String) {
            if (value.startsWith("#")) {
                // common in GWT, and safe, so bypass further checks
            } else if (value.trim { it <= ' ' }.lowercase(Locale.getDefault()).startsWith("javascript:")) {
                // possibly unsafe, we could have random user code here
                // we can't tell if its safe or not so we refuse to accept
                //
                throw RuntimeException("javascript unsafe in href: $value")
            }
        }
    }
}
