package editorx.gui.main.toolbar

import editorx.gui.IconRef
import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.Explorer
import editorx.gui.dialog.PluginManagerDialog
import editorx.util.IconLoader
import java.awt.Insets
import java.awt.Color
import java.awt.event.ActionListener
import javax.swing.*

/**
 * 顶部工具栏：常用操作的快捷入口。
 * 在 macOS 上菜单集成到系统顶栏时，窗口内仍保留此工具栏以便操作。
 */
class ToolBar(private val mainWindow: MainWindow) : JToolBar() {
    companion object {
        private const val ICON_SIZE = 20
    }

    private var toggleSideBarButton: JButton? = null

    init {
        isFloatable = false
        val separator = Color(0xDE, 0xDE, 0xDE)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, separator),
            BorderFactory.createEmptyBorder(2, 5, 2, 5),
        )
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        buildActions()
    }

    private fun JButton.compact(textLabel: String, l: ActionListener): JButton = apply {
        toolTipText = textLabel
        isFocusable = false
        margin = Insets(4, 4, 4, 4)
        addActionListener(l)
    }

    private fun buildActions() {
        /*
         左侧按钮
         */

        /*
         自适应填充
         */
        add(Box.createHorizontalGlue())

        /*
         右侧按钮
         */
        add(JButton(IconLoader.getIcon(IconRef("icons/build.svg"), ICON_SIZE)).compact("编译") {
           // TODO 编译功能待实现
        })

        add(Box.createHorizontalStrut(12))
        addSeparator()
        add(Box.createHorizontalStrut(12))

        toggleSideBarButton = JButton(getSideBarIcon()).compact("切换侧边栏") {
            toggleSideBar()
        }
        add(toggleSideBarButton)
        add(JButton(IconLoader.getIcon(IconRef("icons/settings.svg"), ICON_SIZE)).compact("设置") {
            showSettings()
        })
    }

    private fun openFolder() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "选择文件夹"
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selected = chooser.selectedFile
            mainWindow.guiControl.workspace.openWorkspace(selected)
            (mainWindow.sideBar.getView("explorer") as? Explorer)?.refreshRoot()
        }
    }

    fun updateSideBarIcon(sideBarVisible: Boolean) {
        val iconName = if (sideBarVisible) "icons/layout-sidebar-left.svg" else "icons/layout-sidebar-left-off.svg"
        toggleSideBarButton?.icon = IconLoader.getIcon(IconRef(iconName), ICON_SIZE)
    }

    private fun getSideBarIcon(): Icon? {
        val isVisible = mainWindow.sideBar.isSideBarVisible()
        val iconName = if (isVisible) "icons/layout-sidebar-left.svg" else "icons/layout-sidebar-left-off.svg"
        return IconLoader.getIcon(IconRef(iconName), ICON_SIZE)
    }

    private fun toggleSideBar() {
        val sidebar = mainWindow.sideBar
        if (sidebar.isSideBarVisible()) sidebar.hideSideBar() else sidebar.getCurrentViewId()
            ?.let { sidebar.showView(it) }
        toggleSideBarButton?.icon = getSideBarIcon()
    }

    private fun showPluginManager() {
        val pm = mainWindow.pluginManager ?: return
        PluginManagerDialog(mainWindow, pm).isVisible = true
    }

    private fun showFindDialog() {
        JOptionPane.showMessageDialog(this, "查找功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun showReplaceDialog() {
        JOptionPane.showMessageDialog(this, "替换功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun showSettings() {
        JOptionPane.showMessageDialog(this, "设置界面待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }
}
