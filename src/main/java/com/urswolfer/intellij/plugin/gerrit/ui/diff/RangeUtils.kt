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
package com.urswolfer.intellij.plugin.gerrit.ui.diff

import com.google.gerrit.extensions.client.Comment
import com.intellij.util.text.CharSequenceReader
import java.io.BufferedReader
import java.io.IOException

/**
 * @author Urs Wolfer
 */
object RangeUtils {
    @JvmStatic
    fun textOffsetToRange(charsSequence: CharSequence?, start: Int, end: Int): Comment.Range {
        var startLine = 1
        var startOffset = -1
        var endLine = 1
        var endOffset = -1
        try {
            CharSequenceReader(charsSequence!!).use { charSequenceReader ->
                val reader = BufferedReader(charSequenceReader)
                var lineString: String
                var currentCharCount = 0
                while ((reader.readLine().also { lineString = it }) != null) {
                    currentCharCount += lineString.length
                    currentCharCount++ // line break

                    if (start > currentCharCount) {
                        startLine++
                    } else if (startOffset < 0) {
                        startOffset = start - (currentCharCount - lineString.length - 1)
                    }

                    if (end > currentCharCount) {
                        endLine++
                    } else {
                        endOffset = end - (currentCharCount - lineString.length - 1)
                        break
                    }
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val range = Comment.Range()
        range.startLine = startLine
        range.startCharacter = startOffset
        range.endLine = endLine
        range.endCharacter = endOffset
        return range
    }

    @JvmStatic
    fun rangeToTextOffset(charsSequence: CharSequence?, range: Comment.Range): Offset {
        var startOffset = 0
        var endOffset = 0
        try {
            CharSequenceReader(charsSequence!!).use { charSequenceReader ->
                val reader = BufferedReader(charSequenceReader)
                var line: String
                var textLineCount = 1
                while ((reader.readLine().also { line = it }) != null) {
                    if (textLineCount < range.startLine) {
                        startOffset += line.length
                        startOffset++ // line break
                    }
                    if (textLineCount < range.endLine) {
                        endOffset += line.length
                        endOffset++ // line break
                    } else {
                        break
                    }
                    textLineCount++
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        startOffset += range.startCharacter
        endOffset += range.endCharacter
        return Offset(startOffset, endOffset)
    }

    class Offset(@JvmField val start: Int, @JvmField val end: Int)
}
