/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package com.urswolfer.intellij.plugin.gerrit.util

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import java.io.File

/**
 * @author Thomas Forrer
 */
class PathUtils @Inject constructor(
    private val gerritGitUtil: GerritGitUtil
) {
    fun getRelativePath(project: Project?, absoluteFilePath: String, gerritProjectName: String): String? {
        val repository = gerritGitUtil.getRepositoryForGerritProject(project, gerritProjectName) ?: return null
        val root = repository.root
        return FileUtil.getRelativePath(File(root.path), File(absoluteFilePath))
    }

    /**
     * @return a relative path for all files under the project root, or the absolute path for other files
     */
    fun getRelativeOrAbsolutePath(project: Project?, absoluteFilePath: String, gerritProjectName: String): String? {
        val relativePath = getRelativePath(project, absoluteFilePath, gerritProjectName)
        if (relativePath == null || relativePath.contains(File.separator + "..")) {
            return absoluteFilePath
        }
        return relativePath
    }

    companion object {
        /**
         * Gerrit handles paths always with a forward slash (/). Windows uses backslash (\), so we need to convert them.
         */
        fun ensureSlashSeparators(path: String?): String {
            return path!!.replace('\\', '/')
        }
    }
}
