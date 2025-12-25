package editorx.gui.main.menubar

import editorx.core.i18n.I18n
import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.Explorer
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

class MenuBar(private val mainWindow: MainWindow) : JMenuBar() {
    private val i18nListener: () -> Unit = {
        SwingUtilities.invokeLater { setupMenus() }
    }

    init {
        I18n.addListener(i18nListener)
        setupMenus()
    }

    private fun setupMenus() {
        removeAll()
        add(createFileMenu())
        add(createEditMenu())
        add(createHelpMenu())
        revalidate()
        repaint()
    }

    override fun removeNotify() {
        super.removeNotify()
        I18n.removeListener(i18nListener)
    }

    private fun createFileMenu(): JMenu {
        val shortcut = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        return JMenu(I18n.translate("menu.file")).apply {
            mnemonic = KeyEvent.VK_F

            add(JMenuItem(I18n.translate("action.openFile")).apply {
                mnemonic = KeyEvent.VK_O
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut)
                addActionListener { mainWindow.openFileChooserAndOpen() }
            })
            add(JMenuItem(I18n.translate("action.openFolder")).apply {
                mnemonic = KeyEvent.VK_D
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_D, shortcut)
                addActionListener { openFolder() }
            })

            add(JMenu(I18n.translate("action.recent")).apply {
                addMenuListener(RecentFilesMenuListener(this, mainWindow))
            })

            addSeparator()

            add(JMenuItem(I18n.translate("action.save")).apply {
                mnemonic = KeyEvent.VK_S
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut)
                addActionListener { mainWindow.editor.saveCurrent() }
            })
            add(JMenuItem(I18n.translate("action.saveAs")).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut or InputEvent.SHIFT_DOWN_MASK)
                addActionListener { mainWindow.editor.saveCurrentAs() }
            })

            addSeparator()

            add(JMenuItem(I18n.translate("action.exit")).apply {
                mnemonic = KeyEvent.VK_X
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut)
                addActionListener { System.exit(0) }
            })
        }
    }

    private fun createEditMenu(): JMenu {
        return JMenu(I18n.translate("menu.edit")).apply {
            val shortcut = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
            mnemonic = KeyEvent.VK_E

            add(JMenuItem("撤销").apply {
                mnemonic = KeyEvent.VK_Z
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut)
                isEnabled = false
            })
            add(JMenuItem("重做").apply {
                mnemonic = KeyEvent.VK_Y
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcut)
                isEnabled = false
            })

            addSeparator()

            add(JMenuItem(I18n.translate("action.find")).apply {
                mnemonic = KeyEvent.VK_F
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcut)
                addActionListener { showFindDialog() }
            })
            add(JMenuItem(I18n.translate("action.replace")).apply {
                mnemonic = KeyEvent.VK_R
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcut)
                addActionListener { showReplaceDialog() }
            })
        }
    }

    private fun createHelpMenu(): JMenu {
        return JMenu(I18n.translate("menu.help")).apply {
            mnemonic = KeyEvent.VK_H

            add(JMenuItem(I18n.translate("action.about")).apply { addActionListener { showAbout() } })

            addSeparator()

            add(JMenuItem(I18n.translate("action.help")).apply {
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
            mainWindow.guiContext.workspace.openWorkspace(selectedFolder)
            mainWindow.guiContext.workspace.addRecentWorkspace(selectedFolder)
//            mainWindow.statusBar.setMessage("已打开文件夹: ${selectedFolder.name}")
            (mainWindow.sideBar.getView("explorer") as? Explorer)?.refreshRoot()
            mainWindow.statusBar.updateVcsDisplay()
            mainWindow.editor.showEditorContent()
        }
    }

    private fun showFindDialog() {
        mainWindow.editor.showFindBar()
    }

    private fun showReplaceDialog() {
        mainWindow.editor.showReplaceBar()
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
        val recents = mainWindow.guiContext.workspace.recentFiles()
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
