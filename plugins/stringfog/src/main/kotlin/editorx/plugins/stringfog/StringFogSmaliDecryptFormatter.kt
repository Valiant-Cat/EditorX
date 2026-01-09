package editorx.plugins.stringfog

import editorx.core.filetype.Formatter
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

/**
 * 将 Smali 中形如：
 * invoke-static {vX, vY}, L.../StringFog;->decrypt([B[B)Ljava/lang/String;
 * move-result-object vZ
 *
 * 的调用离线解密为：
 * const-string vZ, "明文"
 *
 * 说明：
 * - 当前实现按 StringFog 默认 xor 算法解密（key 循环异或 data）。
 * - 仅处理 bytes 模式（decrypt([B[B)Ljava/lang/String;）。
 */
class StringFogSmaliDecryptFormatter : Formatter {
    companion object {
        private val logger = LoggerFactory.getLogger(StringFogSmaliDecryptFormatter::class.java)

        private val METHOD_BEGIN_RE = Regex("^\\s*\\.method\\b")
        private val METHOD_END_RE = Regex("^\\s*\\.end\\s+method\\b")

        private val FILL_ARRAY_DATA_RE =
            Regex("^\\s*fill-array-data\\s+([vp]\\d+),\\s*:(\\S+)\\s*$")

        private val INVOKE_DECRYPT_RE = Regex(
            "^\\s*invoke-static(?:/range)?\\s+\\{([^}]*)\\},\\s*\\S+->decrypt\\(\\[B\\[B\\)Ljava/lang/String;\\s*$"
        )

        private val MOVE_RESULT_OBJECT_RE = Regex("^\\s*move-result-object\\s+([vp]\\d+)\\s*$")

        private val ARRAY_DATA_BEGIN_RE = Regex("^\\s*\\.array-data\\s+(\\S+)\\s*$")
        private val ARRAY_DATA_END_RE = Regex("^\\s*\\.end\\s+array-data\\s*$")
    }

    override fun format(content: String): String {
        return decryptWithReport(content).content
    }

    data class DecryptResult(
        val content: String,
        val foundCalls: Int,
        val replacedCalls: Int,
        val skippedCalls: Int,
    )

    fun decryptWithReport(content: String): DecryptResult {
        val hasCrlf = content.contains("\r\n")
        val workingContent = if (hasCrlf) content.replace("\r\n", "\n") else content
        val eol = if (hasCrlf) "\r\n" else "\n"

        // Kotlin 的 split 要求 limit >= 0；使用超大正数来保留末尾空行
        val lines = workingContent.split('\n', limit = Int.MAX_VALUE)
        val arrayDataByLabel = parseArrayDataByLabel(lines)

        val out = StringBuilder(workingContent.length + 128)
        var inMethod = false
        val regToArrayLabel = mutableMapOf<String, String>()

        var i = 0
        var foundCalls = 0
        var replacedCount = 0
        var skippedCalls = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val code = line.substringBefore('#')

            if (METHOD_BEGIN_RE.containsMatchIn(trimmed)) {
                inMethod = true
                regToArrayLabel.clear()
            } else if (METHOD_END_RE.containsMatchIn(trimmed)) {
                inMethod = false
                regToArrayLabel.clear()
            }

            if (inMethod) {
                FILL_ARRAY_DATA_RE.matchEntire(code)?.let { m ->
                    val reg = m.groupValues[1]
                    val label = m.groupValues[2]
                    regToArrayLabel[reg] = label
                }

                val invokeMatch = INVOKE_DECRYPT_RE.matchEntire(code)
                if (invokeMatch != null) {
                    foundCalls++
                    val args = parseInvokeArgs(invokeMatch.groupValues[1])
                    if (args.size == 2) {
                        val dataLabel = regToArrayLabel[args[0]]
                        val keyLabel = regToArrayLabel[args[1]]
                        val dataBytes = dataLabel?.let { arrayDataByLabel[it] }
                        val keyBytes = keyLabel?.let { arrayDataByLabel[it] }

                        val moveIndex = findMoveResultObjectIndex(lines, i + 1)
                        val destReg = moveIndex
                            ?.let { MOVE_RESULT_OBJECT_RE.matchEntire(lines[it].substringBefore('#')) }
                            ?.groupValues
                            ?.getOrNull(1)

                        if (dataBytes != null && keyBytes != null && keyBytes.isNotEmpty() && moveIndex != null && destReg != null) {
                            val plaintext = tryDecryptXorUtf8(dataBytes, keyBytes)
                            if (plaintext != null) {
                                val indent = line.takeWhile { it == ' ' || it == '\t' }
                                val preview = toSingleLinePreview(plaintext)
                                out.append(indent).append("# StringFog.decrypt => ").append(preview).append(eol)
                                out.append(indent)
                                    .append("const-string ")
                                    .append(destReg)
                                    .append(", \"")
                                    .append(escapeSmaliString(plaintext))
                                    .append("\"")
                                    .append(eol)

                                // 保留 invoke 与 move-result 之间的非指令空行（如 .line），但丢弃 move-result-object
                                var k = i + 1
                                while (k < moveIndex) {
                                    out.append(lines[k]).append(eol)
                                    k++
                                }
                                i = moveIndex + 1

                                replacedCount++
                                continue
                            }
                        }
                    }
                    skippedCalls++
                }
            }

            out.append(line)
            if (i != lines.lastIndex) out.append(eol)
            i++
        }

        if (replacedCount > 0) {
            logger.info("StringFog 解密完成：替换 $replacedCount 处 decrypt 调用")
        }
        return DecryptResult(
            content = out.toString(),
            foundCalls = foundCalls,
            replacedCalls = replacedCount,
            skippedCalls = skippedCalls,
        )
    }

    private fun parseArrayDataByLabel(lines: List<String>): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        var i = 0
        while (i < lines.size) {
            val lineTrim = lines[i].substringBefore('#').trim()
            if (lineTrim.startsWith(":")) {
                val label = parseLabelName(lineTrim)
                if (label == null) {
                    i++
                    continue
                }
                val nextLine = lines.getOrNull(i + 1)?.substringBefore('#')?.trim()
                val beginMatch = nextLine?.let { ARRAY_DATA_BEGIN_RE.matchEntire(it) }
                if (beginMatch != null) {
                    val size = parseSmaliInt(beginMatch.groupValues[1])
                    if (size == 1) {
                        val values = mutableListOf<Byte>()
                        var j = i + 2
                        while (j < lines.size) {
                            val t = lines[j].trim()
                            if (ARRAY_DATA_END_RE.matches(t)) {
                                break
                            }

                            val noComment = t.substringBefore('#').trim()
                            if (noComment.isNotEmpty()) {
                                val tokens = noComment.split(Regex("\\s+")).filter { it.isNotBlank() }
                                for (token in tokens) {
                                    parseSmaliByteLiteral(token)?.let { values.add(it) }
                                }
                            }
                            j++
                        }

                        if (j < lines.size && ARRAY_DATA_END_RE.matches(lines[j].trim())) {
                            result[label] = values.toByteArray()
                            i = j + 1
                            continue
                        }
                    }
                }
            }
            i++
        }
        return result
    }

    private fun parseLabelName(trimmedLine: String): String? {
        if (!trimmedLine.startsWith(":")) return null
        val raw = trimmedLine.removePrefix(":")
        val noComment = raw.substringBefore('#')
        val token = noComment.trim().split(Regex("\\s+"), limit = 2).firstOrNull()
        return token?.takeIf { it.isNotBlank() }
    }

    private fun parseSmaliInt(token: String): Int? {
        val t = token.trim()
        return if (t.startsWith("0x", ignoreCase = true)) {
            t.drop(2).toIntOrNull(16)
        } else {
            t.toIntOrNull()
        }
    }

    private fun parseInvokeArgs(args: String): List<String> {
        val trimmed = args.trim()
        if (trimmed.contains("..")) {
            // 处理 invoke-static/range {v0 .. v1}
            val parts = trimmed.split("..")
            if (parts.size != 2) return emptyList()
            val start = parts[0].trim()
            val end = parts[1].trim()
            val expanded = expandRegisterRange(start, end)
            return if (expanded.size >= 2) expanded.take(2) else emptyList()
        }
        return trimmed.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(2)
    }

    private fun expandRegisterRange(start: String, end: String): List<String> {
        val startTrim = start.trim()
        val endTrim = end.trim()
        if (startTrim.length < 2 || endTrim.length < 2) return emptyList()
        val startPrefix = startTrim[0]
        val endPrefix = endTrim[0]
        if (startPrefix != endPrefix) return emptyList()
        val startNum = startTrim.substring(1).toIntOrNull() ?: return emptyList()
        val endNum = endTrim.substring(1).toIntOrNull() ?: return emptyList()
        if (endNum < startNum) return emptyList()
        return (startNum..endNum).map { "$startPrefix$it" }
    }

    private fun findMoveResultObjectIndex(lines: List<String>, startIndex: Int): Int? {
        var i = startIndex
        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.isEmpty() || t.startsWith("#") || isNonCodeDirective(t)) {
                i++
                continue
            }
            val code = lines[i].substringBefore('#')
            return if (MOVE_RESULT_OBJECT_RE.matches(code)) i else null
        }
        return null
    }

    private fun isNonCodeDirective(trimmedLine: String): Boolean {
        return trimmedLine.startsWith(".line") ||
            trimmedLine.startsWith(".local") ||
            trimmedLine.startsWith(".end local") ||
            trimmedLine.startsWith(".param") ||
            trimmedLine.startsWith(".end param") ||
            trimmedLine.startsWith(".prologue") ||
            trimmedLine.startsWith(".epilogue") ||
            trimmedLine.startsWith(".source")
    }

    private fun tryDecryptXorUtf8(data: ByteArray, key: ByteArray): String? {
        return try {
            val out = ByteArray(data.size)
            for (i in data.indices) {
                val b = data[i].toInt() and 0xFF
                val k = key[i % key.size].toInt() and 0xFF
                out[i] = (b xor k).toByte()
            }
            String(out, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.debug("StringFog xor 解密失败", e)
            null
        }
    }

    private fun parseSmaliByteLiteral(token: String): Byte? {
        var t = token.trim()
        if (t.isEmpty()) return null

        // 去掉结尾的类型后缀（如 0x1et / -0x75t）
        if (t.endsWith("t", ignoreCase = true)) {
            t = t.dropLast(1)
        }
        // 去掉可能的逗号
        if (t.endsWith(",")) {
            t = t.dropLast(1)
        }

        val negative = t.startsWith("-")
        val raw = t.removePrefix("-")
        val value: Int = if (raw.startsWith("0x", ignoreCase = true)) {
            val hex = raw.drop(2)
            val v = hex.toIntOrNull(16) ?: return null
            if (negative) -v else v
        } else {
            val v = raw.toIntOrNull() ?: return null
            if (negative) -v else v
        }
        return value.toByte()
    }

    private fun escapeSmaliString(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code in 0x00..0x1F) {
                        sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun toSingleLinePreview(value: String, maxLen: Int = 120): String {
        val single = buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
        return if (single.length <= maxLen) single else single.take(maxLen) + "…"
    }
}
