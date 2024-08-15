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

/** Safely constructs a [SafeHtml], escaping user provided content.  */
class SafeHtmlBuilder : SafeHtml() {
    private val dBuf = BufferDirect()
    private var cb: Buffer = dBuf

    private var sBuf: BufferSealElement? = null
    private var att: AttMap? = null

    val isEmpty: Boolean
        /** @return true if this builder has not had an append occur yet.
         */
        get() = dBuf.isEmpty

    /** @return true if this builder has content appended into it.
     */
    fun hasContent(): Boolean {
        return !isEmpty
    }

    fun append(`in`: Boolean): SafeHtmlBuilder {
        cb.append(`in`)
        return this
    }

    fun append(`in`: Char): SafeHtmlBuilder {
        when (`in`) {
            '&' -> cb.append("&amp;")
            '>' -> cb.append("&gt;")
            '<' -> cb.append("&lt;")
            '"' -> cb.append("&quot;")
            '\'' -> cb.append("&#39;")
            else -> cb.append(`in`)
        }
        return this
    }

    fun append(`in`: Int): SafeHtmlBuilder {
        cb.append(`in`)
        return this
    }

    fun append(`in`: Long): SafeHtmlBuilder {
        cb.append(`in`)
        return this
    }

    fun append(`in`: Float): SafeHtmlBuilder {
        cb.append(`in`)
        return this
    }

    fun append(`in`: Double): SafeHtmlBuilder {
        cb.append(`in`)
        return this
    }

    /** Append already safe HTML as-is, avoiding double escaping.  */
    fun append(`in`: SafeHtml?): SafeHtmlBuilder {
        if (`in` != null) {
            cb.append(`in`.asString())
        }
        return this
    }

    /** Append the string, escaping unsafe characters.  */
    fun append(`in`: String?): SafeHtmlBuilder {
        if (`in` != null) {
            impl.escapeStr(this, `in`)
        }
        return this
    }

    /** Append the string, escaping unsafe characters.  */
    fun append(`in`: StringBuilder?): SafeHtmlBuilder {
        if (`in` != null) {
            append(`in`.toString())
        }
        return this
    }

    /** Append the string, escaping unsafe characters.  */
    fun append(`in`: StringBuffer?): SafeHtmlBuilder {
        if (`in` != null) {
            append(`in`.toString())
        }
        return this
    }

    /** Append the result of toString(), escaping unsafe characters.  */
    fun append(`in`: Any?): SafeHtmlBuilder {
        if (`in` != null) {
            append(`in`.toString())
        }
        return this
    }

    /** Append the string, escaping unsafe characters.  */
    fun append(`in`: CharSequence?): SafeHtmlBuilder {
        if (`in` != null) {
            escapeCS(this, `in`)
        }
        return this
    }

    /**
     * Open an element, appending "`<tagName>`" to the buffer.
     *
     *
     * After the element is open the attributes may be manipulated until the next `append`,
     * `openElement`, `closeSelf` or `closeElement` call.
     *
     * @param tagName name of the HTML element to open.
     */
    fun openElement(tagName: String): SafeHtmlBuilder {
        assert(isElementName(tagName))
        cb.append("<")
        cb.append(tagName)
        if (sBuf == null) {
            att = AttMap()
            sBuf = BufferSealElement(this)
        }
        att!!.reset(tagName)
        cb = sBuf!!
        return this
    }

    /**
     * Get an attribute of the last opened element.
     *
     * @param name name of the attribute to read.
     * @return the attribute value, as a string. The empty string if the attribute has not been
     * assigned a value. The returned string is the raw (unescaped) value.
     */
    fun getAttribute(name: String): String {
        assert(isAttributeName(name))
        assert(cb === sBuf)
        return att!!.get(name)
    }

    /**
     * Set an attribute of the last opened element.
     *
     * @param name name of the attribute to set.
     * @param value value to assign; any existing value is replaced. The value is escaped (if
     * necessary) during the assignment.
     */
    fun setAttribute(name: String, value: String?): SafeHtmlBuilder {
        assert(isAttributeName(name))
        assert(cb === sBuf)
        att!!.set(name, value ?: "")
        return this
    }

    /**
     * Set an attribute of the last opened element.
     *
     * @param name name of the attribute to set.
     * @param value value to assign, any existing value is replaced.
     */
    fun setAttribute(name: String, value: Int): SafeHtmlBuilder {
        return setAttribute(name, value.toString())
    }

    /**
     * Append a new value into a whitespace delimited attribute.
     *
     *
     * If the attribute is not yet assigned, this method sets the attribute. If the attribute is
     * already assigned, the new value is appended onto the end, after appending a single space to
     * delimit the values.
     *
     * @param name name of the attribute to append onto.
     * @param value additional value to append.
     */
    fun appendAttribute(name: String, value: String?): SafeHtmlBuilder {
        if (!value.isNullOrEmpty()) {
            val e = getAttribute(name)
            return setAttribute(name, if (e.isNotEmpty()) "$e $value" else value)
        }
        return this
    }

    /** Set the height attribute of the current element.  */
    fun setHeight(height: Int): SafeHtmlBuilder {
        return setAttribute("height", height)
    }

    /** Set the width attribute of the current element.  */
    fun setWidth(width: Int): SafeHtmlBuilder {
        return setAttribute("width", width)
    }

    /** Set the CSS class name for this element.  */
    fun setStyleName(style: String): SafeHtmlBuilder {
        assert(isCssName(style))
        return setAttribute("class", style)
    }

    /**
     * Add an additional CSS class name to this element.
     *
     *
     * If no CSS class name has been specified yet, this method initializes it to the single name.
     */
    fun addStyleName(style: String): SafeHtmlBuilder {
        assert(isCssName(style))
        return appendAttribute("class", style)
    }

    private fun sealElement0() {
        assert(cb === sBuf)
        cb = dBuf
        att!!.onto(cb, this)
    }

    fun sealElement(): Buffer {
        sealElement0()
        cb.append(">")
        return cb
    }

    /** Close the current element with a self closing suffix ("/ &gt;").  */
    fun closeSelf(): SafeHtmlBuilder {
        sealElement0()
        cb.append(" />")
        return this
    }

    /** Append a closing tag for the named element.  */
    fun closeElement(name: String): SafeHtmlBuilder {
        assert(isElementName(name))
        cb.append("</")
        cb.append(name)
        cb.append(">")
        return this
    }

    /** Append "&amp;nbsp;" - a non-breaking space, useful in empty table cells.  */
    fun nbsp(): SafeHtmlBuilder {
        cb.append("&nbsp;")
        return this
    }

    /** Append "&lt;br /&gt;" - a line break with no attributes  */
    fun br(): SafeHtmlBuilder {
        cb.append("<br />")
        return this
    }

    /** Append "&lt;tr&gt;"; attributes may be set if needed  */
    fun openTr(): SafeHtmlBuilder {
        return openElement("tr")
    }

    /** Append "&lt;/tr&gt;"  */
    fun closeTr(): SafeHtmlBuilder {
        return closeElement("tr")
    }

    /** Append "&lt;td&gt;"; attributes may be set if needed  */
    fun openTd(): SafeHtmlBuilder {
        return openElement("td")
    }

    /** Append "&lt;/td&gt;"  */
    fun closeTd(): SafeHtmlBuilder {
        return closeElement("td")
    }

    /** Append "&lt;th&gt;"; attributes may be set if needed  */
    fun openTh(): SafeHtmlBuilder {
        return openElement("th")
    }

    /** Append "&lt;/th&gt;"  */
    fun closeTh(): SafeHtmlBuilder {
        return closeElement("th")
    }

    /** Append "&lt;div&gt;"; attributes may be set if needed  */
    fun openDiv(): SafeHtmlBuilder {
        return openElement("div")
    }

    /** Append "&lt;/div&gt;"  */
    fun closeDiv(): SafeHtmlBuilder {
        return closeElement("div")
    }

    /** Append "&lt;span&gt;"; attributes may be set if needed  */
    fun openSpan(): SafeHtmlBuilder {
        return openElement("span")
    }

    /** Append "&lt;/span&gt;"  */
    fun closeSpan(): SafeHtmlBuilder {
        return closeElement("span")
    }

    /** Append "&lt;a&gt;"; attributes may be set if needed  */
    fun openAnchor(): SafeHtmlBuilder {
        return openElement("a")
    }

    /** Append "&lt;/a&gt;"  */
    fun closeAnchor(): SafeHtmlBuilder {
        return closeElement("a")
    }

    /** Append "&lt;param name=... value=... /&gt;".  */
    fun paramElement(name: String?, value: String?): SafeHtmlBuilder {
        openElement("param")
        setAttribute("name", name)
        setAttribute("value", value)
        return closeSelf()
    }

    /** @return an immutable [SafeHtml] representation of the buffer.
     */
    fun toSafeHtml(): SafeHtml {
        return SafeHtmlString(asString())
    }

    override fun asString(): String {
        return cb.toString()
    }

    private abstract class Impl {
        abstract fun escapeStr(b: SafeHtmlBuilder, `in`: String)
    }

    private class ServerImpl : Impl() {
        override fun escapeStr(b: SafeHtmlBuilder, `in`: String) {
            escapeCS(b, `in`)
        }
    }

    private class ClientImpl : Impl() {
        override fun escapeStr(b: SafeHtmlBuilder, `in`: String) {
            b.cb.append(escape(`in`))
        }

        companion object {
            private external fun escape(src: String): String? /*-{ return src.replace(/&/g,'&amp;')
                   .replace(/>/g,'&gt;')
                   .replace(/</g,'&lt;')
                   .replace(/"/g,'&quot;')
                   .replace(/'/g,'&#39;');
     }-*/
        }
    }

    companion object {
        private val impl: Impl = ClientImpl()

        private fun escapeCS(b: SafeHtmlBuilder, `in`: CharSequence) {
            for (element in `in`) {
                b.append(element)
            }
        }

        private fun isElementName(name: String): Boolean {
            return name.matches("^[a-zA-Z][a-zA-Z0-9_-]*$".toRegex())
        }

        private fun isAttributeName(name: String): Boolean {
            return isElementName(name)
        }

        private fun isCssName(name: String): Boolean {
            return isElementName(name)
        }
    }
}
