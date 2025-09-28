package editorx.gui.main.titlebar

import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.Explorer
import editorx.gui.dialog.PluginManagerDialog
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

class TitleBar(private val mainWindow: MainWindow) : JMenuBar() {
    init {
        setupMenus()
    }

    private fun setupMenus() {
        add(createFileMenu())
        add(createEditMenu())
        add(createPluginMenu())
        add(createHelpMenu())
    }

    private fun createFileMenu(): JMenu {
        val shortcut = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        return JMenu("文件").apply {
            mnemonic = KeyEvent.VK_F

            add(JMenuItem("打开文件...").apply {
                mnemonic = KeyEvent.VK_O
                accelerator =
                    KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)
                addActionListener { mainWindow.openFileChooserAndOpen() }
            })
            add(JMenuItem("打开文件夹...").apply {
                mnemonic = KeyEvent.VK_D
                accelerator =
                    KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
                addActionListener { openFolder() }
            })

            add(JMenu("最近打开").apply {
                addMenuListener(RecentFilesMenuListener(this, mainWindow))
            })

            addSeparator()

            add(JMenuItem("保存").apply {
                mnemonic = KeyEvent.VK_S
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut)
                addActionListener { mainWindow.editor.saveCurrent() }
            })
            add(JMenuItem("另存为...").apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut or InputEvent.SHIFT_DOWN_MASK)
                addActionListener { mainWindow.editor.saveCurrentAs() }
            })

            addSeparator()

            add(JMenuItem("退出").apply {
                mnemonic = KeyEvent.VK_X
                accelerator =
                    KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK)
                addActionListener { System.exit(0) }
            })
        }
    }

    private fun createEditMenu(): JMenu {
        return JMenu("编辑").apply {
            mnemonic = KeyEvent.VK_E

            add(JMenuItem("撤销").apply {
                mnemonic = KeyEvent.VK_Z
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK)
                isEnabled = false
            })
            add(JMenuItem("重做").apply {
                mnemonic = KeyEvent.VK_Y
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK)
                isEnabled = false
            })

            addSeparator()

            add(JMenuItem("查找...").apply {
                mnemonic = KeyEvent.VK_F
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)
                addActionListener { showFindDialog() }
            })
            add(JMenuItem("替换...").apply {
                mnemonic = KeyEvent.VK_H
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK)
                addActionListener { showReplaceDialog() }
            })
        }
    }

    private fun createPluginMenu(): JMenu {
        return JMenu("插件").apply {
            mnemonic = KeyEvent.VK_T

            val pluginManagerItem =
                JMenuItem("插件管理").apply { addActionListener { showPluginManager() } }

            add(pluginManagerItem)
        }
    }

    private fun createHelpMenu(): JMenu {
        return JMenu("帮助").apply {
            mnemonic = KeyEvent.VK_H

            add(JMenuItem("关于").apply { addActionListener { showAbout() } })

            addSeparator()

            add(JMenuItem("帮助文档").apply {
                mnemonic = KeyEvent.VK_F1
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)
                addActionListener { showHelp() }
            })
        }
    }

    private fun openFolder() {
        val fileChooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "选择文件夹"
            }
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selectedFolder = fileChooser.selectedFile
            // 更新工作区并刷新 Explorer
            mainWindow.guiControl.workspace.openWorkspace(selectedFolder)
            mainWindow.statusBar.setMessage("已打开文件夹: ${selectedFolder.name}")
            (mainWindow.sideBar.getView("explorer") as? Explorer)?.refreshRoot()
        }
    }

    private fun showFindDialog() {
        JOptionPane.showMessageDialog(this, "查找功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun showReplaceDialog() {
        JOptionPane.showMessageDialog(this, "替换功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun toggleSidebar() {
        val sidebar = mainWindow.sideBar
        if (sidebar.isSideBarVisible()) sidebar.hideSideBar()
        else sidebar.getCurrentViewId()?.let { sidebar.showView(it) }
    }

    // 暂时注释掉panel相关方法
    // private fun toggleBottomPanel() {
    //     val panel = mainWindow.panel
    //     if (panel.isPanelVisible()) panel.hidePanel() else panel.getCurrentViewId()?.let {
    // panel.showView(it) }
    // }
    private fun showPluginManager() {
        val pm = mainWindow.pluginManager ?: return
        PluginManagerDialog(mainWindow, pm).isVisible = true
    }

    private fun showSettings() {
        JOptionPane.showMessageDialog(this, "设置界面待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun showAbout() {
        val aboutMessage =
            """
            EditorX v1.0

            一个用于编辑APK文件的工具

            功能特性：
            • 语法高亮编辑
            • 插件系统支持
            • 多标签页界面
            • 文件浏览和管理

            开发：XiaMao Tools
        """.trimIndent()
        JOptionPane.showMessageDialog(
            this,
            aboutMessage,
            "关于 EditorX",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun showHelp() {
        JOptionPane.showMessageDialog(this, "帮助文档待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }
}

private class RecentFilesMenuListener(
    private val menu: JMenu,
    private val mainWindow: MainWindow
) : javax.swing.event.MenuListener {
    override fun menuSelected(e: javax.swing.event.MenuEvent) {
        menu.removeAll()
        val recents = mainWindow.guiControl.workspace.recentFiles()
        if (recents.isEmpty()) {
            menu.add(JMenuItem("(无)"))
        } else {
            recents.forEach { file ->
                val item = JMenuItem(file.name)
                item.toolTipText = file.absolutePath
                item.addActionListener { mainWindow.editor.openFile(file) }
                menu.add(item)
            }
        }
    }

    override fun menuDeselected(e: javax.swing.event.MenuEvent) {}
    override fun menuCanceled(e: javax.swing.event.MenuEvent) {}
}
