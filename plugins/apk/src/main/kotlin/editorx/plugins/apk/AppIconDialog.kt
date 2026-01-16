package editorx.plugins.apk

import editorx.core.gui.GuiExtension
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

object AppIconDialog {
    private val logger = LoggerFactory.getLogger(AppIconDialog::class.java)

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

        val result = AndroidResourcePreviewEditDialog.showImageResource(
            gui = gui,
            title = I18n.translate(I18nKeys.Toolbar.EDIT_APP_ICONS),
            initialResourceRef = current.iconValue ?: "",
            resourceRefLabel = "图标资源",
            candidatesLabel = "图标尺寸",
            footerHintHtml = "<html><small>保存后会按配置替换/生成 res/mipmap*/ 或 res/drawable*/ 下的同名图标文件（png/webp），并更新 AndroidManifest.xml。</small></html>",
            resolveCandidates = { root, ref -> AppInfoEditor.resolveIconCandidates(root, ref) },
            resolvePreviewCandidates = { root, ref -> AppInfoEditor.resolveIconPreviewCandidates(root, ref) },
        ) ?: return

        val update = AndroidAppInfoUpdate(
            packageName = null,
            labelText = null,
            useStringAppName = false,
            labelStringKey = "app_name",
            updateAllLocalesForAppName = false,
            appNameByValuesDir = null,
            removeStringFromValuesDirs = null,
            iconValue = result.resourceRef,
            replaceIconPngFromFile = result.pngFile,
            generateMultiDensityIcons = result.generateMultiDensity,
            createMissingDensityIcons = result.createMissingDensities,
        )

        gui.showProgress("正在更新图标…", indeterminate = true)
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
                    }
                }
            } catch (e: Exception) {
                logger.error("更新图标失败", e)
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
    }
}
