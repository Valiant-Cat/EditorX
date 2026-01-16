package editorx.plugins.apk

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

data class LocalizedStringValue(
    val valuesDir: String,
    val stringsFile: File,
    val value: String?,
)

data class IconCandidate(
    val resDir: String,
    val file: File,
    val width: Int?,
    val height: Int?,
)

data class AndroidAppInfo(
    val packageName: String?,
    val labelValue: String?,
    val resolvedAppNames: List<LocalizedStringValue>,
    val iconValue: String?,
    val roundIconValue: String?,
    val iconCandidates: List<IconCandidate>,
    val roundIconCandidates: List<IconCandidate>,
)

data class AndroidAppInfoUpdate(
    val packageName: String?,
    val labelText: String?,
    val useStringAppName: Boolean,
    val labelStringKey: String,
    val updateAllLocalesForAppName: Boolean,
    val appNameByValuesDir: Map<String, String>?,
    val removeStringFromValuesDirs: Set<String>?,
    val iconValue: String?,
    val replaceIconPngFromFile: File?,
    val generateMultiDensityIcons: Boolean,
    val createMissingDensityIcons: Boolean,
)

object AppInfoEditor {
    private val manifestPackageRegex =
        """<manifest[^>]*\bpackage\s*=\s*["']([^"']+)["']""".toRegex()

    fun isLocaleValuesDirName(dirName: String): Boolean {
        if (dirName == "values") return true
        if (!dirName.startsWith("values-")) return false

        val qualifier = dirName.removePrefix("values-")
        val parts = qualifier.split('-').filter { it.isNotBlank() }
        val first = parts.firstOrNull() ?: return false

        // BCP-47: values-b+zh+Hans+CN
        if (first.startsWith("b+")) return true

        // legacy: values-en / values-zh-rCN / values-en-rUS-night
        if (Regex("^[a-z]{2}$").matches(first)) return true

        // 3-letter language codes exist，但与资源限定符 "car"（UI mode: car）冲突；对 car 做保守处理。
        if (Regex("^[a-z]{3}$").matches(first)) {
            if (first != "car") return true
            val second = parts.getOrNull(1) ?: return false
            return Regex("^r([A-Z]{2}|\\d{3})$").matches(second)
        }

        return false
    }

    fun readAppInfo(workspaceRoot: File): AndroidAppInfo? {
        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.isFile) return null

        val content = runCatching { Files.readString(manifestFile.toPath()) }.getOrNull() ?: return null
        val packageName = manifestPackageRegex.find(content)?.groupValues?.getOrNull(1)

        val applicationStartTag = findStartTag(content, "application") ?: return AndroidAppInfo(
            packageName = packageName,
            labelValue = null,
            resolvedAppNames = emptyList(),
            iconValue = null,
            roundIconValue = null,
            iconCandidates = emptyList(),
            roundIconCandidates = emptyList(),
        )

        val labelValue = extractAttrValue(applicationStartTag, "android:label")
        val iconValue = extractAttrValue(applicationStartTag, "android:icon")
        val roundIconValue = extractAttrValue(applicationStartTag, "android:roundIcon")

        return AndroidAppInfo(
            packageName = packageName,
            labelValue = labelValue,
            resolvedAppNames = resolveAppNameValues(workspaceRoot, labelValue),
            iconValue = iconValue,
            roundIconValue = roundIconValue,
            iconCandidates = findIconCandidates(workspaceRoot, iconValue),
            roundIconCandidates = findIconCandidates(workspaceRoot, roundIconValue),
        )
    }

    fun resolveIconCandidates(workspaceRoot: File, iconValue: String?): List<IconCandidate> {
        return findIconCandidates(workspaceRoot, iconValue)
    }

    fun resolveIconPreviewCandidates(workspaceRoot: File, iconValue: String?): List<IconCandidate> {
        val direct = findIconCandidates(workspaceRoot, iconValue)
        val directImages = direct.filter { it.width != null && it.height != null }
        if (directImages.isNotEmpty()) return directImages

        // 兜底：android:icon 常指向 mipmap-anydpi-v26 下的 adaptive icon（xml），需要再解析出 foreground/background 再找实际图片
        val xmls = direct.filter { it.file.extension.equals("xml", ignoreCase = true) }
        for (xml in xmls) {
            val refs = extractDrawableRefsFromXml(xml.file)
            for (ref in refs) {
                val nested = findIconCandidates(workspaceRoot, ref)
                val nestedImages = nested.filter { it.width != null && it.height != null }
                if (nestedImages.isNotEmpty()) return nestedImages
            }
        }
        return emptyList()
    }

    fun listStringValuesForKey(workspaceRoot: File, key: String): List<LocalizedStringValue> {
        val resRoot = File(workspaceRoot, "res")
        if (!resRoot.isDirectory) return emptyList()

        val valuesDirs = resRoot.listFiles()
            ?.filter { it.isDirectory && isLocaleValuesDirName(it.name) }
            ?.sortedBy { it.name }
            ?: emptyList()

        return valuesDirs.mapNotNull { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (!stringsFile.isFile) return@mapNotNull null
            val content = runCatching { Files.readString(stringsFile.toPath()) }.getOrNull() ?: return@mapNotNull null
            LocalizedStringValue(
                valuesDir = dir.name,
                stringsFile = stringsFile,
                value = extractStringValue(content, key)?.let { unescapeXmlText(it.trim()) }
            )
        }
    }

    fun applyUpdate(workspaceRoot: File, update: AndroidAppInfoUpdate): ApplyResult {
        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.isFile) return ApplyResult(false, "未找到 AndroidManifest.xml")

        val original = runCatching { Files.readString(manifestFile.toPath()) }.getOrNull()
            ?: return ApplyResult(false, "读取 AndroidManifest.xml 失败")

        val originalAppStartTag = findStartTag(original, "application")
        val originalIconValue = originalAppStartTag?.let { extractAttrValue(it, "android:icon") }

        val safePackageName = update.packageName?.trim()?.takeIf { it.isNotEmpty() }
        val safeLabelText = update.labelText?.trim()?.takeIf { it.isNotEmpty() }
        val safeIconValue = update.iconValue?.trim()?.takeIf { it.isNotEmpty() }

        if (safePackageName != null && !isValidJavaPackageName(safePackageName)) {
            return ApplyResult(false, "包名不合法：$safePackageName")
        }
        if (safeIconValue != null && safeIconValue.contains('"')) {
            return ApplyResult(false, "图标值包含非法字符：\"")
        }
        if (safeLabelText != null && safeLabelText.contains('"')) {
            return ApplyResult(false, "应用名称包含非法字符：\"")
        }

        // 0) 可选：用 PNG 替换图标文件（尽量在修改 manifest 前执行，避免出现“失败但已改 manifest”的情况）
        val pngFile = update.replaceIconPngFromFile
        if (pngFile != null) {
            val iconForReplace = safeIconValue ?: originalIconValue
            if (iconForReplace.isNullOrBlank()) {
                return ApplyResult(
                    false,
                    "未设置 android:icon，无法根据资源名替换 PNG（请先填写图标资源引用，例如 @mipmap/ic_launcher）"
                )
            }

            val replaced = replaceResourcePng(
                workspaceRoot,
                iconForReplace,
                pngFile,
                generateMultiDensity = update.generateMultiDensityIcons,
                createMissingDensities = update.createMissingDensityIcons,
            )
            if (!replaced.success) return replaced
        }

        var content = original

        // 1) 更新 manifest package
        if (safePackageName != null) {
            val manifestStartTag = findStartTag(content, "manifest")
                ?: return ApplyResult(false, "解析 AndroidManifest.xml 失败：未找到 <manifest>")
            val updatedManifestTag = upsertAttr(manifestStartTag, "package", safePackageName)
            content = content.replaceFirst(manifestStartTag, updatedManifestTag)
        }

        // 2) 更新 application attributes
        val appStartTag = findStartTag(content, "application")
            ?: return ApplyResult(false, "解析 AndroidManifest.xml 失败：未找到 <application>")

        var updatedAppTag = appStartTag

        if (safeIconValue != null) {
            updatedAppTag = upsertAttr(updatedAppTag, "android:icon", safeIconValue)
        }

        if (safeLabelText != null) {
            if (update.useStringAppName) {
                val key = update.labelStringKey.trim().ifEmpty { "app_name" }
                val stringsResult = upsertStringResourcesForLocales(
                    workspaceRoot = workspaceRoot,
                    key = key,
                    defaultValue = safeLabelText,
                    updateAllExistingLocales = update.updateAllLocalesForAppName,
                    valuesDirToValue = update.appNameByValuesDir,
                    removeValuesDirs = update.removeStringFromValuesDirs,
                )
                if (!stringsResult.success) return stringsResult

                updatedAppTag = upsertAttr(updatedAppTag, "android:label", "@string/$key")
            } else {
                updatedAppTag = upsertAttr(updatedAppTag, "android:label", safeLabelText)
            }
        } else if (update.useStringAppName) {
            // 勾选了“使用 @string/app_name”但未填写名称：视为无效操作
            return ApplyResult(false, "请填写应用名称")
        }

        content = content.replaceFirst(appStartTag, updatedAppTag)

        // 写回 manifest
        runCatching { Files.writeString(manifestFile.toPath(), content) }
            .onFailure { return ApplyResult(false, "写入 AndroidManifest.xml 失败：${it.message ?: "未知错误"}") }

        return ApplyResult(true, "已更新 App 信息")
    }

    data class ApplyResult(val success: Boolean, val message: String)

    private fun isValidJavaPackageName(value: String): Boolean {
        // 粗略校验：a.b.c，段以字母/下划线开头，后续允许字母/数字/下划线
        val segment = """[a-zA-Z_][a-zA-Z0-9_]*"""
        return Regex("^$segment(\\.$segment)*$").matches(value)
    }

    private fun findStartTag(content: String, tagName: String): String? {
        val start = content.indexOf("<$tagName")
        if (start == -1) return null
        val end = findTagEnd(content, start)
        if (end == -1) return null
        return content.substring(start, end + 1)
    }

    private fun findTagEnd(content: String, start: Int): Int {
        var pos = start
        var inQuotes = false
        var quoteChar: Char? = null
        while (pos < content.length) {
            val ch = content[pos]
            when {
                !inQuotes && (ch == '"' || ch == '\'') -> {
                    inQuotes = true
                    quoteChar = ch
                }

                inQuotes && ch == quoteChar -> {
                    inQuotes = false
                    quoteChar = null
                }

                !inQuotes && ch == '>' -> return pos
            }
            pos++
        }
        return -1
    }

    private fun extractAttrValue(tag: String, attrName: String): String? {
        val pattern = """\b${Regex.escape(attrName)}\s*=\s*["']([^"']+)["']""".toRegex()
        return pattern.find(tag)?.groupValues?.getOrNull(1)
    }

    private fun upsertAttr(startTag: String, attrName: String, attrValue: String): String {
        val attrRegex = """\b${Regex.escape(attrName)}\s*=\s*(["'])([^"']*)\1""".toRegex()
        val match = attrRegex.find(startTag)
        if (match != null) {
            val quote = match.groupValues[1]
            val replacement = """$attrName=$quote$attrValue$quote"""
            return startTag.replaceRange(match.range, replacement)
        }

        val insertAt = startTag.lastIndexOf('>')
        if (insertAt == -1) return startTag
        val prefix = startTag.substring(0, insertAt).trimEnd()
        val suffix = startTag.substring(insertAt)
        return "$prefix $attrName=\"$attrValue\"$suffix"
    }

    private fun upsertStringResource(workspaceRoot: File, name: String, value: String): ApplyResult {
        val resDir = File(workspaceRoot, "res/values")
        return upsertStringResourceInDir(resDir, name, value)
    }

    private fun upsertStringResourcesForLocales(
        workspaceRoot: File,
        key: String,
        defaultValue: String,
        updateAllExistingLocales: Boolean,
        valuesDirToValue: Map<String, String>?,
        removeValuesDirs: Set<String>?,
    ): ApplyResult {
        val resRoot = File(workspaceRoot, "res")
        if (!resRoot.isDirectory) return ApplyResult(false, "未找到 res 目录：${resRoot.absolutePath}")

        val allValuesDirs = resRoot.listFiles()
            ?.filter { it.isDirectory && isLocaleValuesDirName(it.name) }
            ?: emptyList()

        // 1) 始终保证 values 目录写入
        val defaultDir = File(resRoot, "values")
        val defaultResult = upsertStringResourceInDir(defaultDir, key, defaultValue)
        if (!defaultResult.success) return defaultResult

        // 1.5) 删除指定 values* 目录下的 key（删除优先级高于写入）
        val removeSet = (removeValuesDirs ?: emptySet())
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "values" }
            .toSet()
        for (dirName in removeSet) {
            val dir = File(resRoot, dirName)
            val r = removeStringResourceInDir(dir, key)
            if (!r.success) return r
        }

        // 2) 若提供了精确的 valuesDir -> value，则按该 map 写入（包含 values-xx 等）
        if (!valuesDirToValue.isNullOrEmpty()) {
            for ((dirName, value) in valuesDirToValue) {
                val dir = File(resRoot, dirName)
                if (!dir.exists() && !dir.mkdirs()) return ApplyResult(false, "创建目录失败：${dir.absolutePath}")
                if (dirName in removeSet) continue
                val r = upsertStringResourceInDir(dir, key, value)
                if (!r.success) return r
            }
            return ApplyResult(true, "已更新 strings.xml（多语言）")
        }

        // 3) 否则按开关决定：是否同步更新所有已存在 values* 目录
        if (updateAllExistingLocales) {
            for (dir in allValuesDirs) {
                if (dir.name == "values") continue
                if (dir.name in removeSet) continue
                val r = upsertStringResourceInDir(dir, key, defaultValue)
                if (!r.success) return r
            }
        }

        return ApplyResult(true, "已更新 strings.xml")
    }

    private fun upsertStringResourceInDir(valuesDir: File, name: String, value: String): ApplyResult {
        if (!valuesDir.exists() && !valuesDir.mkdirs()) {
            return ApplyResult(false, "创建目录失败：${valuesDir.absolutePath}")
        }

        val stringsFile = File(valuesDir, "strings.xml")
        val escaped = escapeXmlText(value)

        if (!stringsFile.exists()) {
            val content = buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("<resources>")
                appendLine("""    <string name="$name">$escaped</string>""")
                appendLine("</resources>")
            }
            return runCatching {
                Files.writeString(stringsFile.toPath(), content, StandardCharsets.UTF_8)
                ApplyResult(true, "已写入 strings.xml")
            }.getOrElse {
                ApplyResult(false, "写入 strings.xml 失败：${it.message ?: "未知错误"}")
            }
        }

        val original = runCatching { Files.readString(stringsFile.toPath()) }.getOrNull()
            ?: return ApplyResult(false, "读取 strings.xml 失败：${stringsFile.absolutePath}")

        val stringRegex =
            """<string\b[^>]*\bname\s*=\s*["']${Regex.escape(name)}["'][^>]*>([\s\S]*?)</string>""".toRegex()
        val match = stringRegex.find(original)
        val updated = if (match != null) {
            original.replaceRange(match.groups[1]!!.range, escaped)
        } else {
            val end = original.lastIndexOf("</resources>")
            if (end == -1) {
                // 兜底：直接追加
                original + "\n" + """<string name="$name">$escaped</string>""" + "\n"
            } else {
                original.substring(0, end) +
                    """    <string name="$name">$escaped</string>""" + "\n" +
                    original.substring(end)
            }
        }

        return runCatching {
            Files.writeString(stringsFile.toPath(), updated, StandardCharsets.UTF_8)
            ApplyResult(true, "已更新 strings.xml")
        }.getOrElse {
            ApplyResult(false, "写入 strings.xml 失败：${it.message ?: "未知错误"}")
        }
    }

    private fun removeStringResourceInDir(valuesDir: File, name: String): ApplyResult {
        if (!valuesDir.exists() || !valuesDir.isDirectory) return ApplyResult(true, "目录不存在，已忽略：${valuesDir.name}")

        val stringsFile = File(valuesDir, "strings.xml")
        if (!stringsFile.exists() || !stringsFile.isFile) return ApplyResult(true, "strings.xml 不存在，已忽略：${stringsFile.absolutePath}")

        val original = runCatching { Files.readString(stringsFile.toPath()) }.getOrNull()
            ?: return ApplyResult(false, "读取 strings.xml 失败：${stringsFile.absolutePath}")

        val stringRegex =
            """\s*<string\b[^>]*\bname\s*=\s*["']${Regex.escape(name)}["'][^>]*>[\s\S]*?</string>\s*""".toRegex()
        if (!stringRegex.containsMatchIn(original)) return ApplyResult(true, "未找到要移除的条目：$name")

        val updated = original.replace(stringRegex, "\n")
        return runCatching {
            Files.writeString(stringsFile.toPath(), updated, StandardCharsets.UTF_8)
            ApplyResult(true, "已移除 $name（${valuesDir.name}）")
        }.getOrElse {
            ApplyResult(false, "写入 strings.xml 失败：${it.message ?: "未知错误"}")
        }
    }

    private fun resolveAppNameValues(workspaceRoot: File, labelValue: String?): List<LocalizedStringValue> {
        val ref = labelValue?.trim() ?: return emptyList()
        val key = parseStringRefKey(ref) ?: return emptyList()

        return listStringValuesForKey(workspaceRoot, key)
    }

    private fun parseStringRefKey(value: String): String? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("@string/")) return null
        val key = trimmed.removePrefix("@string/").trim()
        return key.takeIf { it.isNotBlank() }
    }

    private fun extractStringValue(stringsXml: String, key: String): String? {
        val stringRegex =
            """<string\b[^>]*\bname\s*=\s*["']${Regex.escape(key)}["'][^>]*>([\s\S]*?)</string>""".toRegex()
        return stringRegex.find(stringsXml)?.groups?.get(1)?.value
    }

    private fun unescapeXmlText(text: String): String {
        // 仅回显用途：做最基础的实体反转义
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun findIconCandidates(workspaceRoot: File, iconValue: String?): List<IconCandidate> {
        val ref = iconValue?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val (type, name) = parseAndroidResRef(ref) ?: return emptyList()

        val resRoot = File(workspaceRoot, "res")
        if (!resRoot.isDirectory) return emptyList()

        val dirs = resRoot.listFiles()
            ?.filter { it.isDirectory && (it.name == type || it.name.startsWith("$type-")) }
            ?.sortedBy { it.name }
            ?: emptyList()

        val candidates = mutableListOf<IconCandidate>()
        val supportedExts = setOf("png", "webp", "xml")
        val extOrder = mapOf("png" to 0, "webp" to 1, "xml" to 2)
        for (dir in dirs) {
            val matches = dir.listFiles()
                ?.filter { it.isFile && it.nameWithoutExtension == name && it.extension.lowercase() in supportedExts }
                ?.sortedWith(compareBy<File>({ extOrder[it.extension.lowercase()] ?: 99 }, { it.name }))
                ?: emptyList()
            for (file in matches) {
                val image = runCatching { ImageIO.read(file) }.getOrNull()
                candidates += IconCandidate(dir.name, file, image?.width, image?.height)
            }
        }
        return candidates
    }

    private fun extractDrawableRefsFromXml(xmlFile: File): List<String> {
        if (!xmlFile.isFile) return emptyList()
        val content = runCatching { Files.readString(xmlFile.toPath()) }.getOrNull() ?: return emptyList()

        // 适配 <foreground android:drawable="@mipmap/xxx" /> / <background ...> 等常见写法
        val drawableRegex = """android:drawable\s*=\s*["'](@[^"']+)["']""".toRegex()
        val refs = drawableRegex.findAll(content).mapNotNull { it.groupValues.getOrNull(1)?.trim() }.toList()

        // 优先 foreground，再 background（仅用于预览，不做合成）
        val foreground = refs.firstOrNull { it.contains("foreground", ignoreCase = true) }
        val ordered = buildList {
            if (foreground != null) add(foreground)
            addAll(refs.filter { it != foreground })
        }
        return ordered.distinct()
    }

    private fun escapeXmlText(text: String): String {
        return buildString(text.length) {
            text.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(ch)
                }
            }
        }
    }

    private fun replaceResourcePng(
        workspaceRoot: File,
        resourceValue: String,
        pngFile: File,
        generateMultiDensity: Boolean,
        createMissingDensities: Boolean,
    ): ApplyResult {
        if (!pngFile.isFile) return ApplyResult(false, "选择的 PNG 文件不存在：${pngFile.absolutePath}")

        val (type, name) = parseAndroidResRef(resourceValue)
            ?: return ApplyResult(false, "图标资源引用格式不支持：$resourceValue（期望 @mipmap/name 或 @drawable/name）")

        val resRoot = File(workspaceRoot, "res")
        if (!resRoot.isDirectory) return ApplyResult(false, "未找到 res 目录：${resRoot.absolutePath}")

        // 当前 UI 仅允许用户选择 PNG，因此替换后统一输出为 PNG；
        // 若原来是 WebP，则会删除旧 WebP，仅保留更新后的 PNG（避免同名资源多格式并存）。
        val outputExt = "png"

        val targetDirs = mutableListOf<File>()
        // 先收集已有目录
        val existing = resRoot.listFiles()
            ?.filter { it.isDirectory && (it.name == type || it.name.startsWith("$type-")) }
            ?: emptyList()
        targetDirs += existing

        // 若需要生成密度目录，则补齐常见 density（launcher icon）
        val densityOrder = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        if (createMissingDensities) {
            for (density in densityOrder) {
                val dir = File(resRoot, "$type-$density")
                if (!dir.exists()) {
                    if (!dir.mkdirs()) return ApplyResult(false, "创建目录失败：${dir.absolutePath}")
                }
                if (dir.isDirectory) targetDirs += dir
            }
        }

        // 目标文件列表：替换已有同名 png/webp；若允许补齐，则缺失时新建 png
        val targets = targetDirs.distinctBy { it.absolutePath }
            .filter { it.isDirectory }
            .filter { dir ->
                val existsPng = File(dir, "$name.png").isFile
                val existsWebp = File(dir, "$name.webp").isFile
                existsPng || existsWebp || createMissingDensities
            }
            .map { dir -> File(dir, "$name.$outputExt") }

        if (targets.isEmpty()) {
            return ApplyResult(false, "未找到可替换的图标文件：res/${type}*/$name.(png|webp)")
        }

        fun deleteOtherFormats(target: File) {
            val dir = target.parentFile ?: return
            val base = target.nameWithoutExtension
            val png = File(dir, "$base.png")
            val webp = File(dir, "$base.webp")
            when (outputExt.lowercase()) {
                "png" -> runCatching { Files.deleteIfExists(webp.toPath()) }
                "webp" -> runCatching { Files.deleteIfExists(png.toPath()) }
            }
        }

        if (!generateMultiDensity) {
            return runCatching {
                targets.forEach { target ->
                    target.parentFile?.mkdirs()
                    deleteOtherFormats(target)
                    Files.copy(pngFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                ApplyResult(true, "已替换 ${targets.size} 个图标文件（$resourceValue）")
            }.getOrElse {
                ApplyResult(false, "替换图标文件失败：${it.message ?: "未知错误"}")
            }
        }

        val sourceImage = runCatching { ImageIO.read(pngFile) }.getOrNull()
            ?: return ApplyResult(false, "无法读取 PNG 图标：${pngFile.absolutePath}")

        val densityPx = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192,
        )

        return runCatching {
            targets.forEach { target ->
                target.parentFile?.mkdirs()
                val density = extractDensityQualifier(target.parentFile?.name ?: "")
                val px = density?.let { densityPx[it] }
                if (px == null) {
                    // 兜底：没有密度信息就直接覆盖
                    deleteOtherFormats(target)
                    Files.copy(pngFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } else {
                    val scaled = scaleToSquare(sourceImage, px)
                    deleteOtherFormats(target)
                    ImageIO.write(scaled, "png", target)
                }
            }
            ApplyResult(true, "已替换/生成 ${targets.size} 个图标文件（多密度：$resourceValue）")
        }.getOrElse {
            ApplyResult(false, "替换/生成图标文件失败：${it.message ?: "未知错误"}")
        }
    }

    private fun extractDensityQualifier(resDirName: String): String? {
        // 例如 mipmap-xxhdpi / drawable-xhdpi
        val parts = resDirName.split('-')
        return parts.firstOrNull { it in setOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi") }
    }

    private fun scaleToSquare(source: BufferedImage, size: Int): BufferedImage {
        val dst = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val sw = source.width.coerceAtLeast(1)
            val sh = source.height.coerceAtLeast(1)
            val scale = minOf(size.toDouble() / sw, size.toDouble() / sh)
            val dw = (sw * scale).toInt().coerceAtLeast(1)
            val dh = (sh * scale).toInt().coerceAtLeast(1)
            val x = (size - dw) / 2
            val y = (size - dh) / 2
            g.drawImage(source, x, y, dw, dh, null)
        } finally {
            g.dispose()
        }
        return dst
    }

    private fun parseAndroidResRef(value: String): Pair<String, String>? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("@")) return null
        val parts = trimmed.removePrefix("@").split("/", limit = 2)
        if (parts.size != 2) return null
        val type = parts[0]
        val name = parts[1]
        if (type !in setOf("mipmap", "drawable")) return null
        if (name.isBlank()) return null
        return type to name
    }
}
