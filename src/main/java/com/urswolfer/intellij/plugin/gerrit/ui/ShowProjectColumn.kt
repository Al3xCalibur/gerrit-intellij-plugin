package com.urswolfer.intellij.plugin.gerrit.ui

enum class ShowProjectColumn(private val label: String) {
    ALWAYS("Always"),
    AUTO("Auto (when multiple Git repositories available)"),
    NEVER("Never");

    override fun toString(): String {
        return label
    }
}
