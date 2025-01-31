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

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @author Guilherme Rodriguero
 */
public class ShowWIPFilter extends AbstractChangesFilter {
    private boolean value = true;

    @Override
    public AnAction getAction(Project project) {
        return new ShowWIPActionFilter();
    }

    private void setValue(boolean value) {
        this.value = value;
        setChanged();
        notifyObservers();
    }

    @Nullable
    @Override
    public String getSearchQueryPart() {
        return value ? null : "-is:wip";
    }

    public final class ShowWIPActionFilter extends ToggleAction implements DumbAware, UpdateInBackground {
        public ShowWIPActionFilter() {
            super("WIP Changes", "Display WIP changes", AllIcons.Actions.Profile);
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return value;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
            setValue(state);
        }
    }
}
