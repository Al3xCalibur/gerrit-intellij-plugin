package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

interface MyIcons {
    companion object {
        val Gerrit: Icon = IconLoader.getIcon("/icons/gerrit.svg", MyIcons::class.java)
    }
}
