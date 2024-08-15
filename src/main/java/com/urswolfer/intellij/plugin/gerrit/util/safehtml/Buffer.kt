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

interface Buffer {
    fun append(v: Boolean)

    fun append(v: Char)

    fun append(v: Int)

    fun append(v: Long)

    fun append(v: Float)

    fun append(v: Double)

    fun append(v: String?)

    override fun toString(): String
}
