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

import com.google.gerrit.extensions.client.Comment

/**
 * CommentInfo and ReviewInput.CommentInput do not provide equals and hashCode as required for map handling.
 *
 * @author Urs Wolfer
 */
class CommentHelper(val comment: Comment) {
    override fun equals(obj: Any?): Boolean {
        if (obj !is CommentHelper) {
            return false
        }
        return equals(
            comment,
            obj.comment
        )
    }

    override fun hashCode(): Int {
        return hashCode(comment)
    }

    companion object {
        fun equals(comment1: Comment, comment2: Comment?): Boolean {
            if (comment1 === comment2) return true
            if (comment2 == null || comment1.javaClass != comment2.javaClass) return false

            val that: Comment = comment2

            if (comment1.line != that.line) return false
            if (comment1.id != that.id) return false
            if (comment1.inReplyTo != that.inReplyTo) return false
            if (comment1.message != that.message) return false
            if (comment1.path != that.path) return false
            if (comment1.side != that.side) return false
            if (comment1.updated != that.updated) return false

            return true
        }

        fun hashCode(comment: Comment): Int {
            var result = 0
            result = 31 * result + comment.id.hashCode()
            result = 31 * result + (if (comment.path != null) comment.path.hashCode() else 0)
            result = 31 * result + (if (comment.side != null) comment.side.hashCode() else 0)
            result = 31 * result + comment.line
            result = 31 * result + (if (comment.inReplyTo != null) comment.inReplyTo.hashCode() else 0)
            result = 31 * result + (if (comment.message != null) comment.message.hashCode() else 0)
            result = 31 * result + (if (comment.updated != null) comment.updated.hashCode() else 0)
            return result
        }
    }
}
