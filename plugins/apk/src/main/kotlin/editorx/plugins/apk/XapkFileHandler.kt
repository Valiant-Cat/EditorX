package editorx.plugins.apk

import editorx.core.external.ApkTool
import editorx.core.gui.GuiExtension
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.FileHandler
import org.slf4j.LoggerFactory
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class XapkFileHandler(private val gui: GuiExtension) : FileHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(XapkFileHandler::class.java)
    }

    override fun canHandle(file: File): Boolean {
        return file.isFile && file.extension.lowercase() == "xapk"
    }

    override fun handleOpenFile(file: File): Boolean {
        var result = JOptionPane.CLOSED_OPTION
        if (SwingUtilities.isEventDispatchThread()) {
            result = JOptionPane.showConfirmDialog(
                null,
                I18n.translate(I18nKeys.Dialog.DETECTED_XAPK),
                I18n.translate(I18nKeys.Dialog.OPEN_XAPK_FILE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )
        } else {
            SwingUtilities.invokeAndWait {
                result = JOptionPane.showConfirmDialog(
                    null,
                    I18n.translate(I18nKeys.Dialog.DETECTED_XAPK),
                    I18n.translate(I18nKeys.Dialog.OPEN_XAPK_FILE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                )
            }
        }

        return when (result) {
            JOptionPane.YES_OPTION -> {
                handleXapkFileConversion(file)
                true
            }
            JOptionPane.NO_OPTION -> false
            else -> true
        }
    }

    private fun handleXapkFileConversion(xapkFile: File) {
        Thread {
            try {
                val outputDir = File(xapkFile.parentFile, xapkFile.nameWithoutExtension)

                if (outputDir.exists()) {
                    val options = arrayOf(
                        I18n.translate(I18nKeys.Dialog.OPEN_EXISTING_PROJECT),
                        I18n.translate(I18nKeys.Dialog.REEXTRACT)
                    )
                    var choice = -1
                    SwingUtilities.invokeAndWait {
                        choice = JOptionPane.showOptionDialog(
                            null,
                            I18n.translate(I18nKeys.Dialog.XAPK_DIR_EXISTS).format(outputDir.name),
                            I18n.translate(I18nKeys.Dialog.XAPK_DIR_EXISTS_TITLE),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]
                        )
                    }

                    when (choice) {
                        JOptionPane.YES_OPTION -> {
                            ensureApktoolProjects(outputDir)
                            SwingUtilities.invokeLater { gui.openWorkspace(outputDir) }
                            return@Thread
                        }
                        JOptionPane.NO_OPTION -> {
                            // continue
                        }
                        else -> return@Thread
                    }
                }

                SwingUtilities.invokeLater {
                    gui.showProgress(I18n.translate(I18nKeys.ToolbarMessage.EXTRACTING_XAPK), indeterminate = true)
                }

                if (outputDir.exists()) {
                    deleteRecursively(outputDir)
                }

                ArchiveUtils.extractZip(xapkFile, outputDir)

                val apkEntries = findApkEntries(outputDir)
                XapkWorkspaceMarker.mark(outputDir, xapkFile.name, apkEntries)

                if (apkEntries.isNotEmpty()) {
                    SwingUtilities.invokeLater {
                        gui.showProgress(I18n.translate(I18nKeys.ToolbarMessage.DECOMPILING_XAPK), indeterminate = true)
                    }
                    decompileApks(outputDir, apkEntries)
                }

                SwingUtilities.invokeLater {
                    gui.hideProgress()
                    gui.openWorkspace(outputDir)
                }
            } catch (e: Exception) {
                logger.error("解压 XAPK 失败", e)
                SwingUtilities.invokeLater {
                    gui.hideProgress()
                    JOptionPane.showMessageDialog(
                        null,
                        I18n.translate(I18nKeys.Dialog.XAPK_EXTRACT_FAILED).format(e.message ?: "unknown"),
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.start()
    }

    private fun ensureApktoolProjects(outputDir: File) {
        val apkEntries = findApkEntries(outputDir)
        if (apkEntries.isEmpty()) return
        val hasProject = apkEntries.any { entry ->
            val apkFile = File(outputDir, entry)
            val projectDir = File(apkFile.parentFile, apkFile.nameWithoutExtension)
            File(projectDir, "apktool.yml").isFile
        }
        if (!hasProject) {
            SwingUtilities.invokeLater {
                gui.showProgress(I18n.translate(I18nKeys.ToolbarMessage.DECOMPILING_XAPK), indeterminate = true)
            }
            decompileApks(outputDir, apkEntries)
            SwingUtilities.invokeLater { gui.hideProgress() }
        }
    }

    private fun decompileApks(outputDir: File, entries: List<String>) {
        entries.forEach { entry ->
            val apkFile = File(outputDir, entry)
            if (!apkFile.isFile) return@forEach
            val projectDir = File(apkFile.parentFile, apkFile.nameWithoutExtension)
            if (projectDir.exists()) {
                deleteRecursively(projectDir)
            }
            val result = ApkTool.decompile(apkFile, projectDir, force = true) { false }
            if (result.status == ApkTool.Status.NOT_FOUND) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        null,
                        I18n.translate(I18nKeys.ToolbarMessage.APKTOOL_NOT_FOUND_DETAIL),
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                return
            }
            if (result.status == ApkTool.Status.FAILED) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        null,
                        I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED_DETAIL)
                            .format(result.exitCode, result.output),
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                return
            }
        }
    }

    private fun findApkEntries(outputDir: File): List<String> {
        val root = outputDir.canonicalFile.toPath()
        return outputDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .mapNotNull {
                runCatching { root.relativize(it.canonicalFile.toPath()).toString().replace('\\', '/') }.getOrNull()
            }
            .sorted()
            .toList()
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
