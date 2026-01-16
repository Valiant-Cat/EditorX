package editorx.plugins.aar

import editorx.core.external.Baksmali
import editorx.core.external.D8
import editorx.core.gui.GuiExtension
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.FileHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class AarFileHandler(private val gui: GuiExtension) : FileHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(AarFileHandler::class.java)
    }

    override fun canHandle(file: File): Boolean {
        return file.isFile && file.extension.lowercase() == "aar"
    }

    override fun handleOpenFile(file: File): Boolean {
        var result = JOptionPane.CLOSED_OPTION
        if (SwingUtilities.isEventDispatchThread()) {
            result = JOptionPane.showConfirmDialog(
                null,
                I18n.translate(I18nKeys.Dialog.DETECTED_AAR),
                I18n.translate(I18nKeys.Dialog.OPEN_AAR_FILE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )
        } else {
            SwingUtilities.invokeAndWait {
                result = JOptionPane.showConfirmDialog(
                    null,
                    I18n.translate(I18nKeys.Dialog.DETECTED_AAR),
                    I18n.translate(I18nKeys.Dialog.OPEN_AAR_FILE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                )
            }
        }

        return when (result) {
            JOptionPane.YES_OPTION -> {
                handleAarFileConversion(file)
                true
            }
            JOptionPane.NO_OPTION -> false
            else -> true
        }
    }

    private fun handleAarFileConversion(aarFile: File) {
        Thread {
            try {
                val outputDir = File(aarFile.parentFile, aarFile.nameWithoutExtension)

                if (outputDir.exists()) {
                    val options = arrayOf(
                        I18n.translate(I18nKeys.Dialog.OPEN_EXISTING_PROJECT),
                        I18n.translate(I18nKeys.Dialog.REEXTRACT)
                    )
                    var choice = -1
                    SwingUtilities.invokeAndWait {
                        choice = JOptionPane.showOptionDialog(
                            null,
                            I18n.translate(I18nKeys.Dialog.AAR_DIR_EXISTS).format(outputDir.name),
                            I18n.translate(I18nKeys.Dialog.AAR_DIR_EXISTS_TITLE),
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
                                    gui.showProgress(I18n.translate(I18nKeys.ToolbarMessage.DECOMPILING_AAR), indeterminate = true)
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
                    gui.showProgress(I18n.translate(I18nKeys.ToolbarMessage.EXTRACTING_AAR), indeterminate = true)
                }

                if (outputDir.exists()) {
                    deleteRecursively(outputDir)
                }

                extractAar(aarFile, outputDir)
                AarWorkspaceMarker.mark(outputDir, aarFile.name)

                generateSmali(outputDir)

                SwingUtilities.invokeLater {
                    gui.hideProgress()
                    gui.openWorkspace(outputDir)
                }
            } catch (e: Exception) {
                logger.error("解压 AAR 失败", e)
                SwingUtilities.invokeLater {
                    gui.hideProgress()
                    JOptionPane.showMessageDialog(
                        null,
                        I18n.translate(I18nKeys.Dialog.AAR_EXTRACT_FAILED).format(e.message ?: "unknown"),
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.start()
    }

    private fun extractAar(aarFile: File, outputDir: File) {
        val outputRoot = outputDir.canonicalFile
        ZipFile(aarFile).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(outputDir, entry.name)
                val canonicalOut = outFile.canonicalFile
                if (!canonicalOut.path.startsWith(outputRoot.path + File.separator)) {
                    throw IllegalStateException("zip entry is outside target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun generateSmali(outputDir: File) {
        val classesJar = File(outputDir, "classes.jar")
        if (!classesJar.isFile) {
            logger.warn("AAR 缺少 classes.jar，无法生成 smali")
            return
        }

        if (D8.locate() == null) {
            logger.info("未找到 d8，跳过 AAR smali 生成")
            return
        }
        if (Baksmali.locate() == null) {
            logger.info("未找到 baksmali，跳过 AAR smali 生成")
            return
        }

        val inputs = mutableListOf<File>()
        inputs += classesJar
        val libsDir = File(outputDir, "libs")
        if (libsDir.isDirectory) {
            libsDir.listFiles { _, name -> name.endsWith(".jar", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.let { inputs.addAll(it) }
        }

        val tempDexDir = Files.createTempDirectory("aar_dex_").toFile()
        try {
            val libs = D8.resolveAndroidJar()?.let { listOf(it) } ?: emptyList()
            val dexResult = D8.dexJars(inputs, tempDexDir, libs)
            if (dexResult.status != D8.Status.SUCCESS) {
                logger.warn("AAR 转 DEX 失败: {}", dexResult.output)
                return
            }

            val dexFiles = tempDexDir.listFiles { _, name ->
                name.matches(Regex("""classes\d*\.dex"""))
            }?.sortedBy { it.name } ?: emptyList()
            if (dexFiles.isEmpty()) {
                logger.warn("AAR 未生成任何 DEX 文件")
                return
            }

            dexFiles.forEachIndexed { index, dexFile ->
                val smaliDir = if (index == 0) {
                    File(outputDir, "smali")
                } else {
                    File(outputDir, "smali_classes${index + 1}")
                }
                val result = Baksmali.disassemble(dexFile, smaliDir)
                if (result.status != Baksmali.Status.SUCCESS) {
                    logger.warn("baksmali 反编译失败: {}", result.output)
                } else {
                    ensureWritable(smaliDir)
                }
            }
        } catch (e: Exception) {
            logger.warn("生成 AAR smali 失败", e)
        } finally {
            deleteRecursively(tempDexDir)
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

    private fun hasSmali(outputDir: File): Boolean {
        val smaliDir = File(outputDir, "smali")
        if (smaliDir.isDirectory && smaliDir.listFiles()?.isNotEmpty() == true) return true
        val other = outputDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("smali_classes")
        } ?: emptyArray()
        return other.any { it.listFiles()?.isNotEmpty() == true }
    }
}
