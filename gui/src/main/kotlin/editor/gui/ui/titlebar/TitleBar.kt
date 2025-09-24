package editor.gui.ui.titlebar

import editor.gui.MainFrame
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

class TitleBar(_owner: JFrame) : JMenuBar() {
    init { setupMenus() }

    private fun setupMenus() {
        add(createFileMenu()); add(createEditMenu()); add(createViewMenu()); add(createToolsMenu()); add(createHelpMenu())
    }

    private fun createFileMenu(): JMenu {
        val fileMenu = JMenu("文件").apply { mnemonic = KeyEvent.VK_F }
        val openApkItem = JMenuItem("打开 APK...").apply {
            mnemonic = KeyEvent.VK_O
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK)
            addActionListener { openApkFile() }
        }
        val openFolderItem = JMenuItem("打开文件夹...").apply {
            mnemonic = KeyEvent.VK_D
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_D, ActionEvent.CTRL_MASK)
            addActionListener { openFolder() }
        }
        fileMenu.add(openApkItem); fileMenu.add(openFolderItem); fileMenu.addSeparator()
        val recentFilesMenu = JMenu("最近打开"); fileMenu.add(recentFilesMenu); fileMenu.addSeparator()
        val exitItem = JMenuItem("退出").apply {
            mnemonic = KeyEvent.VK_X
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK)
            addActionListener { System.exit(0) }
        }
        fileMenu.add(exitItem)
        return fileMenu
    }

    private fun createEditMenu(): JMenu {
        val editMenu = JMenu("编辑").apply { mnemonic = KeyEvent.VK_E }
        val undoItem = JMenuItem("撤销").apply { mnemonic = KeyEvent.VK_Z; accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK); isEnabled = false }
        val redoItem = JMenuItem("重做").apply { mnemonic = KeyEvent.VK_Y; accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK); isEnabled = false }
        editMenu.add(undoItem); editMenu.add(redoItem); editMenu.addSeparator()
        val findItem = JMenuItem("查找...").apply { mnemonic = KeyEvent.VK_F; accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK); addActionListener { showFindDialog() } }
        val replaceItem = JMenuItem("替换...").apply { mnemonic = KeyEvent.VK_H; accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_H, ActionEvent.CTRL_MASK); addActionListener { showReplaceDialog() } }
        editMenu.add(findItem); editMenu.add(replaceItem)
        return editMenu
    }

    private fun createViewMenu(): JMenu {
        val viewMenu = JMenu("视图").apply { mnemonic = KeyEvent.VK_V }
        val toggleSidebarItem = JMenuItem("切换侧边栏").apply { mnemonic = KeyEvent.VK_B; accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_B, ActionEvent.CTRL_MASK); addActionListener { toggleSidebar() } }
        val togglePanelItem = JMenuItem("切换底部面板").apply { mnemonic = KeyEvent.VK_P; accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK); addActionListener { toggleBottomPanel() } }
        viewMenu.add(toggleSidebarItem); viewMenu.add(togglePanelItem)
        return viewMenu
    }

    private fun createToolsMenu(): JMenu {
        val toolsMenu = JMenu("工具").apply { mnemonic = KeyEvent.VK_T }
        val pluginManagerItem = JMenuItem("插件管理...").apply { addActionListener { showPluginManager() } }
        val settingsItem = JMenuItem("设置...").apply { mnemonic = KeyEvent.VK_S; addActionListener { showSettings() } }
        toolsMenu.add(pluginManagerItem); toolsMenu.addSeparator(); toolsMenu.add(settingsItem)
        return toolsMenu
    }

    private fun createHelpMenu(): JMenu {
        val helpMenu = JMenu("帮助").apply { mnemonic = KeyEvent.VK_H }
        val aboutItem = JMenuItem("关于").apply { addActionListener { showAbout() } }
        val helpItem = JMenuItem("帮助文档").apply { mnemonic = KeyEvent.VK_F1; accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0); addActionListener { showHelp() } }
        helpMenu.add(helpItem); helpMenu.addSeparator(); helpMenu.add(aboutItem)
        return helpMenu
    }

    private fun openApkFile() {
        val fileChooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("APK Files", "apk"); dialogTitle = "选择APK文件" }
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            MainFrame.instance.statusBar.setMessage("已加载: ${selectedFile.name}")
        }
    }

    private fun openFolder() {
        val fileChooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "选择文件夹" }
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selectedFolder = fileChooser.selectedFile
            MainFrame.instance.statusBar.setMessage("已打开文件夹: ${selectedFolder.name}")
        }
    }

    private fun showFindDialog() { JOptionPane.showMessageDialog(this, "查找功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE) }
    private fun showReplaceDialog() { JOptionPane.showMessageDialog(this, "替换功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE) }
    private fun toggleSidebar() { val sidebar = MainFrame.instance.sideBar; sidebar.isVisible = !sidebar.isVisible }
    private fun toggleBottomPanel() { val panel = MainFrame.instance.panel; panel.isVisible = !panel.isVisible }
    private fun showPluginManager() { JOptionPane.showMessageDialog(this, "插件管理器待实现", "提示", JOptionPane.INFORMATION_MESSAGE) }
    private fun showSettings() { JOptionPane.showMessageDialog(this, "设置界面待实现", "提示", JOptionPane.INFORMATION_MESSAGE) }
    private fun showAbout() {
        val aboutMessage = """
            APK Editor v1.0

            一个用于编辑APK文件的工具

            功能特性：
            • 语法高亮编辑
            • 插件系统支持
            • 多标签页界面
            • 文件浏览和管理

            开发：XiaMao Tools
        """.trimIndent()
        JOptionPane.showMessageDialog(this, aboutMessage, "关于 APK Editor", JOptionPane.INFORMATION_MESSAGE)
    }
    private fun showHelp() { JOptionPane.showMessageDialog(this, "帮助文档待实现", "提示", JOptionPane.INFORMATION_MESSAGE) }
}

