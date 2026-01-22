package editorx.plugins.apk

import editorx.core.external.Baksmali
import editorx.core.gui.GuiExtension
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.FileHandler
import org.slf4j.LoggerFactory
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class AabFileHandler(private val gui: GuiExtension) : FileHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(AabFileHandler::class.java)
    }

    override fun canHandle(file: File): Boolean {
        return file.isFile && file.extension.lowercase() == "aab"
    }

    override fun handleOpenFile(file: File): Boolean {
        var result = JOptionPane.CLOSED_OPTION
        if (SwingUtilities.isEventDispatchThread()) {
            result = JOptionPane.showConfirmDialog(
                null,
                I18n.translate(I18nKeys.Dialog.DETECTED_AAB),
                I18n.translate(I18nKeys.Dialog.OPEN_AAB_FILE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )
        } else {
            SwingUtilities.invokeAndWait {
                result = JOptionPane.showConfirmDialog(
                    null,
                    I18n.translate(I18nKeys.Dialog.DETECTED_AAB),
                    I18n.translate(I18nKeys.Dialog.OPEN_AAB_FILE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                )
            }
        }

        return when (result) {
            JOptionPane.YES_OPTION -> {
                handleAabFileConversion(file)
                true
            }
            JOptionPane.NO_OPTION -> false
            else -> true
        }
    }

    private fun handleAabFileConversion(aabFile: File) {
        Thread {
            try {
                val outputDir = File(aabFile.parentFile, aabFile.nameWithoutExtension)

                if (outputDir.exists()) {
                    val options = arrayOf(
                        I18n.translate(I18nKeys.Dialog.OPEN_EXISTING_PROJECT),
                        I18n.translate(I18nKeys.Dialog.REEXTRACT)
                    )
                    var choice = -1
                    SwingUtilities.invokeAndWait {
                        choice = JOptionPane.showOptionDialog(
                            null,
                            I18n.translate(I18nKeys.Dialog.AAB_DIR_EXISTS).format(outputDir.name),
                            I18n.translate(I18nKeys.Dialog.AAB_DIR_EXISTS_TITLE),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]
                        )
                    }

                    when (choice) {
                        JOptionPane.YES_OPTION -> {
                            if (!hasSmali(outputDir)) {
                                SwingUtilities.invokeLater {
                                    gui.showProgress(
                                        I18n.translate(I18nKeys.ToolbarMessage.DECOMPILING_AAB),
                                        indeterminate = true
                                    )
                                }
                                generateSmali(outputDir)
                                SwingUtilities.invokeLater { gui.hideProgress() }
                            }
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
                    gui.showProgress(I18n.translate(I18nKeys.ToolbarMessage.EXTRACTING_AAB), indeterminate = true)
                }

                if (outputDir.exists()) {
                    deleteRecursively(outputDir)
                }

                ArchiveUtils.extractZip(aabFile, outputDir)
                AabWorkspaceMarker.mark(outputDir, aabFile.name)

                SwingUtilities.invokeLater {
                    gui.showProgress(I18n.translate(I18nKeys.ToolbarMessage.DECOMPILING_AAB), indeterminate = true)
                }
                generateSmali(outputDir)

                SwingUtilities.invokeLater {
                    gui.hideProgress()
                    gui.openWorkspace(outputDir)
                }
            } catch (e: Exception) {
                logger.error("解压 AAB 失败", e)
                SwingUtilities.invokeLater {
                    gui.hideProgress()
                    JOptionPane.showMessageDialog(
                        null,
                        I18n.translate(I18nKeys.Dialog.AAB_EXTRACT_FAILED).format(e.message ?: "unknown"),
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.start()
    }

    private fun generateSmali(outputDir: File) {
        if (Baksmali.locate() == null) {
            logger.info("未找到 baksmali，跳过 AAB smali 生成")
            return
        }

        val moduleDirs = outputDir.listFiles { file ->
            file.isDirectory && File(file, "dex").isDirectory
        }?.sortedBy { it.name } ?: emptyList()

        moduleDirs.forEach { module ->
            val dexDir = File(module, "dex")
            val dexFiles = dexDir.listFiles { _, name ->
                name.matches(Regex("""classes\d*\.dex"""))
            }?.sortedBy { it.name } ?: emptyList()

            dexFiles.forEachIndexed { index, dexFile ->
                val smaliDir = if (index == 0) {
                    File(module, "smali")
                } else {
                    File(module, "smali_classes${index + 1}")
                }
                val result = Baksmali.disassemble(dexFile, smaliDir)
                if (result.status != Baksmali.Status.SUCCESS) {
                    logger.warn("baksmali 反编译失败: {}", result.output)
                } else {
                    ensureWritable(smaliDir)
                }
            }
        }
    }

    private fun hasSmali(outputDir: File): Boolean {
        val moduleDirs = outputDir.listFiles { file ->
            file.isDirectory && File(file, "dex").isDirectory
        } ?: emptyArray()
        return moduleDirs.any { module ->
            val smaliDir = File(module, "smali")
            if (smaliDir.isDirectory && smaliDir.listFiles()?.isNotEmpty() == true) return@any true
            val others = module.listFiles { file ->
                file.isDirectory && file.name.startsWith("smali_classes")
            } ?: emptyArray()
            others.any { it.listFiles()?.isNotEmpty() == true }
        }
    }

    private fun ensureWritable(dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().forEach { file ->
            if (!file.canWrite()) {
                runCatching { file.setWritable(true) }
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
