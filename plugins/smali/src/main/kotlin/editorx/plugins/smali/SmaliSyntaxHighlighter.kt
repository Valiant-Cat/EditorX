package editorx.plugins.smali

import editorx.syntax.SyntaxHighlighter

/**
 * Smali 语法高亮器（Kotlin 实现）
 *
 * 参考官方术语：
 * - Directives: .class, .method, .field 等（结构声明）
 * - Instructions: invoke-virtual, const-string, return-void 等（可执行字节码）
 */
class SmaliSyntaxHighlighter : SyntaxHighlighter {

    override fun highlight(line: String): String {
        if (line.isEmpty()) return line

        // === 1. 分离代码与注释 ===
        val commentIndex = line.indexOf('#')
        val (codePart, commentPart) = if (commentIndex >= 0) {
            line.substring(0, commentIndex) to line.substring(commentIndex)
        } else {
            line to ""
        }

        val result = StringBuilder()
        var pos = 0
        val code = codePart

        while (pos < code.length) {
            val c = code[pos]

            // 跳过空白
            if (c.isWhitespace()) {
                result.append(c)
                pos++
                continue
            }

            var matched = false

            // --- 1. 字符串字面量: "..."（支持转义）---
            if (c == '"') {
                var i = pos + 1
                var escaped = false
                while (i < code.length) {
                    val ch = code[i]
                    if (!escaped && ch == '"') {
                        break
                    }
                    escaped = !escaped && ch == '\\'
                    i++
                }
                if (i < code.length && code[i] == '"') {
                    val str = code.substring(pos, i + 1)
                    result.append(tag("STRING", str))
                    pos = i + 1
                    matched = true
                }
            }

            // --- 2. 指令（Instructions）：按长度降序匹配，避免 const 匹配 const-string ---
            if (!matched) {
                for (inst in INSTRUCTIONS) {
                    if (code.startsWith(inst, pos)) {
                        result.append(tag("INSTRUCTION", inst))
                        pos += inst.length
                        matched = true
                        break
                    }
                }
            }

            // --- 3. 伪指令（Directives）---
            if (!matched) {
                for (dir in DIRECTIVES) {
                    if (code.startsWith(dir, pos)) {
                        result.append(tag("DIRECTIVE", dir))
                        pos += dir.length
                        matched = true
                        break
                    }
                }
            }

            // --- 4. 访问标志（Keywords）---
            if (!matched) {
                for (flag in ACCESS_FLAGS) {
                    if (code.startsWith(flag, pos) && (pos + flag.length == code.length || !code[pos + flag.length].isLetterOrDigit())) {
                        result.append(tag("KEYWORD", flag))
                        pos += flag.length
                        matched = true
                        break
                    }
                }
            }

            // --- 5. 类型描述符：Lxxx; 或 [I, [[Ljava/lang/String; 等 ---
            if (!matched) {
                if (c == 'L' || c == '[') {
                    var i = pos
                    while (i < code.length) {
                        val ch = code[i]
                        if (ch == ';') {
                            val type = code.substring(pos, i + 1)
                            result.append(tag("TYPE", type))
                            pos = i + 1
                            matched = true
                            break
                        }
                        if (!ch.isLetterOrDigit() && ch != '/' && ch != '$' && ch != '[') break
                        i++
                    }
                }
            }

            // --- 6. 寄存器：v0, p1, v100 等 ---
            if (!matched && (c == 'v' || c == 'p')) {
                var i = pos + 1
                while (i < code.length && code[i].isDigit()) i++
                if (i > pos + 1) {
                    val reg = code.substring(pos, i)
                    result.append(tag("REGISTER", reg))
                    pos = i
                    matched = true
                }
            }

            // --- 7. 十六进制数字：0x1a, 0XFF ---
            if (!matched && pos + 1 < code.length && c == '0' && code[pos + 1].lowercaseChar() == 'x') {
                var i = pos + 2
                while (i < code.length && code[i].let { it in "0123456789abcdefABCDEF" }) i++
                if (i > pos + 2) {
                    val hex = code.substring(pos, i)
                    result.append(tag("NUMBER", hex))
                    pos = i
                    matched = true
                }
            }

            // --- 8. 十进制整数（可带负号）---
            if (!matched && (c.isDigit() || (c == '-' && pos + 1 < code.length && code[pos + 1].isDigit()))) {
                var i = if (c == '-') pos + 1 else pos
                while (i < code.length && code[i].isDigit()) i++
                val num = code.substring(pos, i)
                result.append(tag("NUMBER", num))
                pos = i
                matched = true
            }

            // --- 9. 未识别 token：原样输出 ---
            if (!matched) {
                result.append(c)
                pos++
            }
        }

        // 拼接注释（如果有）
        if (commentPart.isNotEmpty()) {
            result.append(tag("COMMENT", commentPart))
        }

        return result.toString()
    }

    private fun tag(type: String, content: String): String = "【$type:$content】"

    // =============== Smali 语法元素定义 ===============

    companion object {
        // Directives（伪指令）— 官方术语
        private val DIRECTIVES = setOf(
            ".class", ".super", ".source", ".implements", ".field", ".end field",
            ".method", ".end method", ".annotation", ".end annotation",
            ".registers", ".locals", ".parameter", ".catch", ".prologue", ".line"
        )

        // Instructions（指令）— 官方术语，按长度降序排列以避免前缀冲突
        private val INSTRUCTIONS = listOf(
            "invoke-polymorphic", "invoke-interface/range", "invoke-static/range",
            "invoke-direct/range", "invoke-virtual/range", "invoke-interface",
            "invoke-static", "invoke-direct", "invoke-virtual",
            "const-string/jumbo", "const-string", "const-wide/high16", "const-wide/16",
            "const-wide", "const/high16", "const/16", "const/4", "const",
            "return-object", "return-wide", "return-void", "return",
            "move-result-object", "move-result-wide", "move-result",
            "new-instance", "check-cast", "instance-of",
            "goto/32", "goto/16", "goto",
            "if-eqz", "if-nez", "if-ltz", "if-lez", "if-gtz", "if-gez",
            "if-eq", "if-ne", "if-lt", "if-le", "if-gt", "if-ge",
            "add-float", "sub-float", "mul-float", "div-float", "rem-float",
            "add-double", "sub-double", "mul-double", "div-double", "rem-double",
            "add-long", "sub-long", "mul-long", "div-long", "rem-long",
            "add-int", "sub-int", "mul-int", "div-int", "rem-int",
            "and-int", "or-int", "xor-int", "shl-int", "shr-int", "ushr-int",
            "neg-int", "not-int"
        ).sortedByDescending { it.length } // 关键：长指令优先匹配

        // 访问标志（关键字）
        private val ACCESS_FLAGS = setOf(
            "public", "private", "protected", "static", "final", "synchronized",
            "bridge", "varargs", "native", "abstract", "strictfp", "synthetic", "enum"
        )
    }
}