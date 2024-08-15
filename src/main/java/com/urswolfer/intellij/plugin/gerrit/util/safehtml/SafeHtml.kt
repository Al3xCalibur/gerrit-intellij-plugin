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

/** Immutable string safely placed as HTML without further escaping.  */
abstract class SafeHtml {
    /** Convert bare http:// and https:// URLs into &lt;a href&gt; tags.  */
    fun linkify(): SafeHtml {
        val part = "(?:[a-zA-Z0-9\$_+!*'%;:@=?#/~-]|&(?!lt;|gt;)|[.,](?!(?:\\s|$)))"
        return replaceAll(
            "(https?://$part{2,}(?:[(]$part*[)])*$part*)",
            "<a href=\"$1\" target=\"_blank\" rel=\"nofollow\">$1</a>"
        )
    }

    /**
     * Apply [.linkify], and "\n\n" to &lt;p&gt;.
     *
     *
     * Lines that start with whitespace are assumed to be preformatted.
     */
    fun wikify(): SafeHtml? {
        val r = SafeHtmlBuilder()
        for (p in linkify().asString().split("\n\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (isQuote(p)) {
                wikifyQuote(r, p)
            } else if (isPreFormat(p)) {
                r.openElement("pre")
                for (line in p.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    r.append(asis(line))
                    r.br()
                }
                r.closeElement("pre")
            } else if (isList(p)) {
                wikifyList(r, p)
            } else {
                r.openElement("p")
                r.append(asis(p))
                r.closeElement("p")
            }
        }
        return r.toSafeHtml()
    }

    private fun wikifyList(r: SafeHtmlBuilder, p: String) {
        var in_ul = false
        var in_p = false
        for (line in p.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            var line = line
            if (line.startsWith("-") || line.startsWith("*")) {
                if (!in_ul) {
                    if (in_p) {
                        in_p = false
                        r.closeElement("p")
                    }

                    in_ul = true
                    r.openElement("ul")
                }
                line = line.substring(1).trim { it <= ' ' }
            } else if (!in_ul) {
                if (!in_p) {
                    in_p = true
                    r.openElement("p")
                } else {
                    r.append(' ')
                }
                r.append(asis(line))
                continue
            }

            r.openElement("li")
            r.append(asis(line))
            r.closeElement("li")
        }

        if (in_ul) {
            r.closeElement("ul")
        } else if (in_p) {
            r.closeElement("p")
        }
    }

    private fun wikifyQuote(r: SafeHtmlBuilder, p: String) {
        var p = p
        r.openElement("blockquote")
        if (p.startsWith("&gt; ")) {
            p = p.substring(5)
        } else if (p.startsWith(" &gt; ")) {
            p = p.substring(6)
        }
        p = p.replace("\\n ?&gt; ".toRegex(), "\n")
        for (e in p.split("\n\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (isQuote(e)) {
                val b = SafeHtmlBuilder()
                wikifyQuote(b, e)
                r.append(b)
            } else {
                r.append(asis(e))
            }
        }
        r.closeElement("blockquote")
    }

    /**
     * Replace first occurrence of `regex` with `repl` .
     *
     *
     * **WARNING:** This replacement is being performed against an otherwise safe HTML string.
     * The caller must ensure that the replacement does not introduce cross-site scripting attack
     * entry points.
     *
     * @param regex regular expression pattern to match the substring with.
     * @param repl replacement expression. Capture groups within `regex` can be referenced with
     * `$<i>n</i>`.
     * @return a new string, after the replacement has been made.
     */
    fun replaceFirst(regex: String, repl: String?): SafeHtml {
        return SafeHtmlString(asString().replaceFirst(regex.toRegex(), repl!!))
    }

    /**
     * Replace each occurrence of `regex` with `repl` .
     *
     *
     * **WARNING:** This replacement is being performed against an otherwise safe HTML string.
     * The caller must ensure that the replacement does not introduce cross-site scripting attack
     * entry points.
     *
     * @param regex regular expression pattern to match substrings with.
     * @param repl replacement expression. Capture groups within `regex` can be referenced with
     * `$<i>n</i>`.
     * @return a new string, after the replacements have been made.
     */
    fun replaceAll(regex: String, repl: String?): SafeHtml {
        return SafeHtmlString(asString().replace(regex.toRegex(), repl!!))
    }

    /** @return a clean HTML string safe for inclusion in any context.
     */
    abstract fun asString(): String

    companion object {
        /** @return the existing HTML text, wrapped in a safe buffer.
         */
        fun asis(htmlText: String): SafeHtml {
            return SafeHtmlString(htmlText)
        }

        private fun isQuote(p: String): Boolean {
            return p.startsWith("&gt; ") || p.startsWith(" &gt; ")
        }

        private fun isPreFormat(p: String): Boolean {
            return p.contains("\n ") || p.contains("\n\t") || p.startsWith(" ") || p.startsWith("\t")
        }

        private fun isList(p: String): Boolean {
            return p.contains("\n- ") || p.contains("\n* ") || p.startsWith("- ") || p.startsWith("* ")
        }
    }
}
