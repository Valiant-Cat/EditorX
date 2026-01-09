package editorx.plugins.android

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.gui.GuiExtension
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.awt.KeyboardFocusManager
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities

object AppInfoDialog {
    private val logger = LoggerFactory.getLogger(AppInfoDialog::class.java)

    fun show(gui: GuiExtension) {
        val workspaceRoot = gui.getWorkspaceRoot()
        if (workspaceRoot == null) {
            JOptionPane.showMessageDialog(
                null,
                I18n.translate(I18nKeys.ToolbarMessage.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val current = AppInfoEditor.readAppInfo(workspaceRoot)
        if (current == null) {
            JOptionPane.showMessageDialog(
                null,
                "未找到或无法读取 AndroidManifest.xml",
                I18n.translate(I18nKeys.Dialog.ERROR),
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val packageField = JTextField(current.packageName ?: "").apply { preferredSize = Dimension(360, 26) }
        val labelRaw = (current.labelValue ?: "").trim()
        val labelField = JTextField(labelRaw).apply {
            preferredSize = Dimension(360, 26)
            isEditable = false
            toolTipText = if (labelRaw.startsWith("@string/")) "资源引用：$labelRaw" else "固定字符串（非 @string）：$labelRaw"
        }
        val iconRaw = (current.iconValue ?: "").trim()
        val iconField = JTextField(iconRaw).apply {
            preferredSize = Dimension(360, 26)
            isEditable = false
            toolTipText = iconRaw
        }

        val openLocalesButton = JButton(I18n.translate(I18nKeys.Toolbar.EDIT_APP_LOCALES) + "…").apply {
            addActionListener {
                AppNameDialog.show(gui)
                refreshFieldsFromWorkspace(gui, packageField, labelField, iconField)
            }
        }

        val openDensitiesButton = JButton(I18n.translate(I18nKeys.Toolbar.EDIT_APP_ICONS) + "…").apply {
            addActionListener {
                AppIconDialog.show(gui)
                refreshFieldsFromWorkspace(gui, packageField, labelField, iconField)
            }
        }

        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
            add(buildForm(
                packageField = packageField,
                labelField = labelField,
                openLocalesButton = openLocalesButton,
                iconField = iconField,
                openDensitiesButton = openDensitiesButton,
            ), BorderLayout.CENTER)
            add(
                JLabel(
                    "<html><small>提示：应用名称/桌面图标为只读，请通过右侧按钮修改；主弹窗仅允许修改包名。</small></html>"
                ),
                BorderLayout.SOUTH
            )
        }

        val option = showDialogWithButtons(
            title = I18n.translate(I18nKeys.Toolbar.EDIT_APP_INFO),
            content = panel,
        )
        if (option == 0) return

        val desiredPackage = packageField.text.trim()
        val currentPackage = (current.packageName ?: "").trim()
        if (desiredPackage.isEmpty()) {
            JOptionPane.showMessageDialog(
                null,
                "包名不能为空",
                I18n.translate(I18nKeys.Dialog.ERROR),
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val shouldBuild = option == 2

        fun runBuild() {
            val packageForName = desiredPackage.ifEmpty { currentPackage }.ifEmpty { "unknown.package" }
            val projectName = workspaceRoot.name.ifEmpty { "project" }
            val distDir = File(workspaceRoot, "dist").apply { mkdirs() }
            val safePackage = sanitizeFilePart(packageForName)
            val safeProject = sanitizeFilePart(projectName)

            var output = File(distDir, "${safeProject}_${safePackage}.apk")
            var index = 1
            while (output.exists()) {
                output = File(distDir, "${safeProject}_${safePackage}_$index.apk")
                index++
            }

            gui.showProgress("正在构建 APK…", indeterminate = true)
            Thread {
                try {
                    val service = ApkBuildService()
                    val result = service.buildTo(workspaceRoot, output) { msg ->
                        SwingUtilities.invokeLater { gui.showProgress(msg, indeterminate = true) }
                    }
                    SwingUtilities.invokeLater {
                        gui.hideProgress()
                        if (result.status == editorx.core.service.BuildStatus.SUCCESS) {
                            gui.refreshExplorer(preserveSelection = true)
                            JOptionPane.showMessageDialog(
                                null,
                                I18n.translate(I18nKeys.ToolbarMessage.BUILD_GENERATED)
                                    .format(output.absolutePath),
                                I18n.translate(I18nKeys.ToolbarMessage.COMPILE_COMPLETE),
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        } else {
                            JOptionPane.showMessageDialog(
                                null,
                                result.output ?: (result.errorMessage ?: "构建失败"),
                                I18n.translate(I18nKeys.Dialog.ERROR),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("构建失败", e)
                    SwingUtilities.invokeLater {
                        gui.hideProgress()
                        JOptionPane.showMessageDialog(
                            null,
                            "构建失败：${e.message ?: "未知错误"}",
                            I18n.translate(I18nKeys.Dialog.ERROR),
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }.start()
        }

        if (desiredPackage != currentPackage) {
            val update = AndroidAppInfoUpdate(
                packageName = desiredPackage,
                labelText = null,
                useStringAppName = false,
                labelStringKey = "app_name",
                updateAllLocalesForAppName = false,
                appNameByValuesDir = null,
                removeStringFromValuesDirs = null,
                iconValue = null,
                replaceIconPngFromFile = null,
                generateMultiDensityIcons = false,
                createMissingDensityIcons = false,
            )

            gui.showProgress("正在更新 App 信息…", indeterminate = true)
            Thread {
                try {
                    val result = AppInfoEditor.applyUpdate(workspaceRoot, update)
                    SwingUtilities.invokeLater {
                        gui.hideProgress()
                        if (!result.success) {
                            JOptionPane.showMessageDialog(
                                null,
                                result.message,
                                I18n.translate(I18nKeys.Dialog.ERROR),
                                JOptionPane.ERROR_MESSAGE
                            )
                            return@invokeLater
                        }
                        // 更新成功不提示；如选择“保存并构建”，继续构建
                        if (shouldBuild) runBuild()
                    }
                } catch (e: Exception) {
                    logger.error("更新 App 信息失败", e)
                    SwingUtilities.invokeLater {
                        gui.hideProgress()
                        JOptionPane.showMessageDialog(
                            null,
                            "更新失败：${e.message ?: "未知错误"}",
                            I18n.translate(I18nKeys.Dialog.ERROR),
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }.start()
        } else {
            // 未修改包名：仅保存不提示；保存并构建则直接构建
            if (shouldBuild) runBuild()
        }
    }

    private fun buildForm(
        packageField: JTextField,
        labelField: JTextField,
        openLocalesButton: JButton,
        iconField: JTextField,
        openDensitiesButton: JButton,
    ): JPanel {
        val grid = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
        }

        fun row(label: String, comp: java.awt.Component) {
            c.gridx = 0
            c.weightx = 0.0
            c.fill = GridBagConstraints.NONE
            c.anchor = GridBagConstraints.WEST
            grid.add(JLabel(label), c)

            c.gridx = 1
            c.weightx = 1.0
            c.fill = GridBagConstraints.HORIZONTAL
            grid.add(comp, c)

            c.gridy++
        }

        row("包名（manifest package）", packageField)
        val nameRow = JPanel(GridBagLayout())
        val nc = GridBagConstraints().apply {
            insets = Insets(0, 0, 0, 8)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
        }
        nameRow.add(labelField, nc)
        nc.gridx = 1
        nc.weightx = 0.0
        nc.insets = Insets(0, 0, 0, 0)
        nameRow.add(openLocalesButton, nc)
        row("应用名称（android:label）", nameRow)

        val iconRow = JPanel(GridBagLayout())
        val ic = GridBagConstraints().apply {
            insets = Insets(0, 0, 0, 8)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
        }
        iconRow.add(iconField, ic)
        ic.gridx = 1
        ic.weightx = 0.0
        ic.insets = Insets(0, 0, 0, 0)
        iconRow.add(openDensitiesButton, ic)
        row("桌面图标（android:icon）", iconRow)

        return grid
    }

    private fun refreshFieldsFromWorkspace(
        gui: GuiExtension,
        packageField: JTextField,
        labelField: JTextField,
        iconField: JTextField,
    ) {
        val root = gui.getWorkspaceRoot() ?: return
        val current = AppInfoEditor.readAppInfo(root) ?: return
        packageField.text = current.packageName ?: ""
        labelField.text = current.labelValue ?: ""
        iconField.text = current.iconValue ?: ""
    }

    private fun sanitizeFilePart(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return "unknown"
        return s.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    /**
     * 自定义弹窗按钮区域，强制按钮从左到右顺序为：取消 / 仅保存 / 保存并构建。
     *
     * @return 0=取消/关闭，1=仅保存，2=保存并构建
     */
    private fun showDialogWithButtons(title: String, content: JPanel): Int {
        if (!SwingUtilities.isEventDispatchThread()) {
            var r = 0
            SwingUtilities.invokeAndWait { r = showDialogWithButtons(title, content) }
            return r
        }

        var result = 0

        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val dialog =
            when (owner) {
                is java.awt.Frame -> JDialog(owner, title, true)
                is java.awt.Dialog -> JDialog(owner, title, true)
                else -> JDialog(null as java.awt.Frame?, title, true)
            }

        dialog.apply {
            var saveButtonRef: JButton? = null
            isResizable = false
            contentPane = JPanel(BorderLayout()).apply {
                add(content, BorderLayout.CENTER)

                val footer = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8))
                val cancelButton = JButton("取消").apply {
                    addActionListener {
                        result = 0
                        dialog.dispose()
                    }
                }
                val saveButton = JButton("仅保存").apply {
                    addActionListener {
                        result = 1
                        dialog.dispose()
                    }
                }
                val saveBuildButton = JButton("保存并构建").apply {
                    addActionListener {
                        result = 2
                        dialog.dispose()
                    }
                }
                saveButtonRef = saveButton

                // 从左到右：取消 / 仅保存 / 保存并构建
                footer.add(cancelButton)
                footer.add(saveButton)
                footer.add(saveBuildButton)
                add(footer, BorderLayout.SOUTH)
            }
            rootPane.defaultButton = saveButtonRef

            rootPane.registerKeyboardAction(
                { dialog.dispose() },
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
            )

            pack()
            setLocationRelativeTo(owner)
        }

        dialog.isVisible = true
        return result
    }

}
