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

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.gerrit.extensions.client.Comment;

import java.util.Objects;

/**
 * CommentInfo and ReviewInput.CommentInput do not provide equals and hashCode as required for map handling.
 *
 * @author Urs Wolfer
 */
public class CommentHelper {

    private final Comment comment;

    public CommentHelper(Comment comment) {
        this.comment = comment;
    }

    public Comment getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommentHelper)) {
            return false;
        }
        return equals(comment, ((CommentHelper) obj).getComment());
    }

    @Override
    public int hashCode() {
        return hashCode(comment);
    }

    public static boolean equals(Comment comment1, Comment comment2) {
        if (comment1 == comment2) return true;
        if (comment2 == null || comment1.getClass() != comment2.getClass()) return false;

        Comment that = comment2;

        if (!Objects.equals(comment1.line, that.line)) return false;
        if (!Objects.equals(comment1.id, that.id)) return false;
        if (!Objects.equals(comment1.inReplyTo, that.inReplyTo)) return false;
        if (!Objects.equals(comment1.message, that.message)) return false;
        if (!Objects.equals(comment1.path, that.path)) return false;
        if (!Objects.equals(comment1.side, that.side)) return false;
        if (!Objects.equals(comment1.updated, that.updated)) return false;

        return true;
    }

    public static int hashCode(Comment comment) {
        int result = 0;
        result = 31 * result + comment.id.hashCode();
        result = 31 * result + (comment.path != null ? comment.path.hashCode() : 0);
        result = 31 * result + (comment.side != null ? comment.side.hashCode() : 0);
        result = 31 * result + comment.line;
        result = 31 * result + (comment.inReplyTo != null ? comment.inReplyTo.hashCode() : 0);
        result = 31 * result + (comment.message != null ? comment.message.hashCode() : 0);
        result = 31 * result + (comment.updated != null ? comment.updated.hashCode() : 0);
        return result;
    }
}
