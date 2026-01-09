package editorx.core.gui

/**
 * Diff 片段（hunk）的行号信息。
 *
 * 行号均为 1-based，与 `git diff` hunk 头一致：`@@ -a,b +c,d @@`
 */
data class DiffHunk(
    val leftStart: Int,
    val leftCount: Int,
    val rightStart: Int,
    val rightCount: Int,
)

