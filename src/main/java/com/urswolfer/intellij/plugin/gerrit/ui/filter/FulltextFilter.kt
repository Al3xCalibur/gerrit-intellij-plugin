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
package com.urswolfer.intellij.plugin.gerrit.ui.filter

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.SearchFieldAction

/**
 * @author Thomas Forrer
 */
class FulltextFilter : AbstractChangesFilter() {
    private var value = ""

    override fun getAction(project: Project?): AnAction {
        return object : SearchFieldAction("Filter: ") {
            override fun actionPerformed(event: AnActionEvent) {
                val newValue = text.trim { it <= ' ' }
                if (isNewValue(newValue)) {
                    value = newValue
                    setChanged()
                    notifyObservers(project)
                }
            }

            private fun isNewValue(newValue: String): Boolean {
                return newValue != value
            }
        }
    }

    override val searchQueryPart: String?
        get() = if (!value.isEmpty()) "(" + specialEncodeFulltextQuery(value) + ")" else null

    companion object {
        /**
         * Queries have some special encoding. `URLEncoder.encode(query, "UTF-8")` does not
         * produce correct encoding for the query. It (falsely) encodes brackets, which are expected
         * to remain in the query string as is... This implementation aims to encode only the most
         * commonly used character is the query.
         * @param query a query string to encode
         * @return an encoded version of the passed `query`
         */
        fun specialEncodeFulltextQuery(query: String): String {
            return query
                .replace("{", "%7B")
                .replace("}", "%7D")
                .replace("+", "%2B")
                .replace(' ', '+')
                .replace("\"", "%22")
                .replace("\\", "%5C")
                .replace("%", "%25")
                .replace("<", "%3C")
                .replace(">", "%3E")
                .replace("^", "%5E")
        }
    }
}
