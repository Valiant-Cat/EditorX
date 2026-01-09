package editorx.gui.workbench.menubar

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.gui.MainWindow
import editorx.gui.core.FileHandlerManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*
import javax.swing.event.MenuEvent
import javax.swing.event.MenuListener

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
        return JMenu(I18n.translate(I18nKeys.Menu.FILE)).apply {
            mnemonic = KeyEvent.VK_F

            add(JMenuItem(I18n.translate(I18nKeys.Action.OPEN_FILE)).apply {
                mnemonic = KeyEvent.VK_O
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut)
                addActionListener { openFileChooserAndOpen() }
            })
            add(JMenuItem(I18n.translate(I18nKeys.Action.OPEN_FOLDER)).apply {
                mnemonic = KeyEvent.VK_D
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_D, shortcut)
                addActionListener { openFolder() }
            })

            add(createRecentProjectsMenu())

            addSeparator()

            add(JMenuItem(I18n.translate(I18nKeys.Action.SAVE)).apply {
                mnemonic = KeyEvent.VK_S
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut)
                addActionListener { mainWindow.editor.saveCurrent() }
            })
            add(JMenuItem(I18n.translate(I18nKeys.Action.SAVE_AS)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut or InputEvent.SHIFT_DOWN_MASK)
                addActionListener { mainWindow.editor.saveCurrentAs() }
            })

            addSeparator()

            add(JMenuItem(I18n.translate(I18nKeys.Action.EXIT)).apply {
                mnemonic = KeyEvent.VK_X
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut)
                addActionListener { System.exit(0) }
            })
        }
    }

    private fun createRecentProjectsMenu(): JMenu {
        return JMenu(I18n.translate(I18nKeys.Welcome.RECENT_PROJECTS)).apply {
            mnemonic = KeyEvent.VK_R
            addMenuListener(
                object : MenuListener {
                    override fun menuSelected(e: MenuEvent?) {
                        populateRecentProjectsMenu(this@apply)
                    }

                    override fun menuDeselected(e: MenuEvent?) {
                        // nop
                    }

                    override fun menuCanceled(e: MenuEvent?) {
                        // nop
                    }
                }
            )
            populateRecentProjectsMenu(this)
        }
    }

    private fun populateRecentProjectsMenu(menu: JMenu) {
        menu.removeAll()
        val recentWorkspaces: List<File> = mainWindow.guiContext.getWorkspace().recentWorkspaces()
        if (recentWorkspaces.isEmpty()) {
            menu.add(JMenuItem(I18n.translate(I18nKeys.Welcome.NO_RECENT_PROJECTS)).apply { isEnabled = false })
            return
        }

        recentWorkspaces.take(20).forEachIndexed { idx, workspace ->
            val item = JMenuItem("${idx + 1}. ${workspace.name}").apply {
                toolTipText = workspace.absolutePath
                addActionListener { mainWindow.openWorkspace(workspace) }
            }
            menu.add(item)
        }
    }

    private fun createEditMenu(): JMenu {
        return JMenu(I18n.translate(I18nKeys.Menu.EDIT)).apply {
            val shortcut = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
            mnemonic = KeyEvent.VK_E

            add(JMenuItem(I18n.translate(I18nKeys.Action.UNDO)).apply {
                mnemonic = KeyEvent.VK_Z
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut)
                isEnabled = false
            })
            add(JMenuItem(I18n.translate(I18nKeys.Action.REDO)).apply {
                mnemonic = KeyEvent.VK_Y
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcut)
                isEnabled = false
            })

            addSeparator()

            add(JMenuItem(I18n.translate(I18nKeys.Action.FIND)).apply {
                mnemonic = KeyEvent.VK_F
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcut)
                addActionListener { showFindDialog() }
            })
            add(JMenuItem(I18n.translate(I18nKeys.Action.REPLACE)).apply {
                mnemonic = KeyEvent.VK_R
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcut)
                addActionListener { showReplaceDialog() }
            })
        }
    }

    private fun createHelpMenu(): JMenu {
        return JMenu(I18n.translate(I18nKeys.Menu.HELP)).apply {
            mnemonic = KeyEvent.VK_H

            add(JMenuItem(I18n.translate(I18nKeys.Action.ABOUT)).apply { addActionListener { showAbout() } })

            addSeparator()

            add(JMenuItem(I18n.translate(I18nKeys.Action.HELP)).apply {
                mnemonic = KeyEvent.VK_F1
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)
                addActionListener { showHelp() }
            })
        }
    }

    private fun openFileChooserAndOpen() {
        mainWindow.showFileChooser { selectedFile ->
            if (selectedFile != null) {
                FileHandlerManager.handleOpenFile(selectedFile)
            }
        }
    }

    private fun openFolder() {
        mainWindow.showFolderChooser { selectedDir ->
            if (selectedDir != null && selectedDir.isDirectory) {
                mainWindow.openWorkspace(selectedDir)
            }
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
        JOptionPane.showMessageDialog(
            this,
            I18n.translate(I18nKeys.Dialog.ABOUT_MESSAGE),
            I18n.translate(I18nKeys.Dialog.ABOUT_TITLE),
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun showHelp() {
        JOptionPane.showMessageDialog(
            this,
            I18n.translate(I18nKeys.Dialog.HELP_NOT_IMPLEMENTED),
            I18n.translate(I18nKeys.Dialog.TIP),
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}
