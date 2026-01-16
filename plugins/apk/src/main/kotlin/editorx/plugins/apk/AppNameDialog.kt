package editorx.plugins.apk

import editorx.core.gui.GuiExtension
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

object AppNameDialog {
    private val logger = LoggerFactory.getLogger(AppNameDialog::class.java)

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

        val initialKey = extractStringKeyOrDefault(current.labelValue)
        val existingForKey = AppInfoEditor.listStringValuesForKey(workspaceRoot, initialKey.trim().ifEmpty { "app_name" })
        val defaultExisting = existingForKey.firstOrNull { it.valuesDir == "values" }?.value
        val rawLabel = current.labelValue?.trim().orEmpty()
        val initialDefaultValue = when {
            !defaultExisting.isNullOrBlank() -> defaultExisting
            rawLabel.isNotEmpty() && !rawLabel.startsWith("@string/") -> rawLabel
            else -> ""
        }

        val result = StringResourceLocalesDialog.show(
            workspaceRoot = workspaceRoot,
            title = I18n.translate(I18nKeys.Toolbar.EDIT_APP_LOCALES),
            initialKey = initialKey,
            initialDefaultValue = initialDefaultValue,
            hintHtml = "<html><small>保存后会写入 res/values*/strings.xml，并将 AndroidManifest.xml 的 android:label 指向 @string/&lt;key&gt;。</small></html>",
        )
        if (result == null) return

        val update = AndroidAppInfoUpdate(
            packageName = null,
            labelText = result.defaultValue,
            useStringAppName = true,
            labelStringKey = result.key,
            updateAllLocalesForAppName = result.syncAllExistingLocalesToDefault,
            appNameByValuesDir = result.overridesByValuesDir,
            removeStringFromValuesDirs = result.removedValuesDirs.takeIf { it.isNotEmpty() },
            iconValue = null,
            replaceIconPngFromFile = null,
            generateMultiDensityIcons = false,
            createMissingDensityIcons = false,
        )

        gui.showProgress("正在更新多语言…", indeterminate = true)
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
                logger.error("更新多语言失败", e)
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

    private fun extractStringKeyOrDefault(labelValue: String?): String {
        val raw = labelValue?.trim().orEmpty()
        if (raw.startsWith("@string/")) {
            val key = raw.removePrefix("@string/").trim()
            if (key.isNotEmpty()) return key
        }
        return "app_name"
    }
}
