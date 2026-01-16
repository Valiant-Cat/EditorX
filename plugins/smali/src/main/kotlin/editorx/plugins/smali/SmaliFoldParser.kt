package editorx.plugins.smali

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.folding.Fold
import org.fife.ui.rsyntaxtextarea.folding.FoldParser
import org.fife.ui.rsyntaxtextarea.folding.FoldType
import javax.swing.text.BadLocationException

class SmaliFoldParser : FoldParser {
    private data class OpenFold(val fold: Fold, val endToken: String)

    override fun getFolds(textArea: RSyntaxTextArea): MutableList<Fold> {
        val folds = mutableListOf<Fold>()
        val stack = ArrayDeque<OpenFold>()

        val lineCount = textArea.lineCount
        val doc = textArea.document

        for (line in 0 until lineCount) {
            val startOffset = try {
                textArea.getLineStartOffset(line)
            } catch (_: Exception) {
                continue
            }
            val endOffset = try {
                textArea.getLineEndOffset(line)
            } catch (_: Exception) {
                continue
            }
            val raw = try {
                doc.getText(startOffset, endOffset - startOffset)
            } catch (_: Exception) {
                ""
            }
            val text = raw.trim()

            when {
                text.startsWith(".method") -> {
                    pushFold(textArea, folds, stack, startOffset, ".end method")
                }
                text.startsWith(".annotation") -> {
                    pushFold(textArea, folds, stack, startOffset, ".end annotation")
                }
                text.startsWith(".packed-switch") -> {
                    pushFold(textArea, folds, stack, startOffset, ".end packed-switch")
                }
                text.startsWith(".sparse-switch") -> {
                    pushFold(textArea, folds, stack, startOffset, ".end sparse-switch")
                }
                text.startsWith(".array-data") -> {
                    pushFold(textArea, folds, stack, startOffset, ".end array-data")
                }
                text.startsWith(".end ") -> {
                    val endToken = text
                    if (stack.isNotEmpty() && stack.last().endToken == endToken) {
                        val open = stack.removeLast()
                        runCatching { open.fold.setEndOffset(endOffset) }
                    }
                }
            }
        }

        return folds
    }

    private fun pushFold(
        textArea: RSyntaxTextArea,
        roots: MutableList<Fold>,
        stack: ArrayDeque<OpenFold>,
        startOffset: Int,
        endToken: String
    ) {
        try {
            val fold =
                if (stack.isEmpty()) Fold(FoldType.CODE, textArea, startOffset)
                else stack.last().fold.createChild(FoldType.CODE, startOffset)
            if (stack.isEmpty()) roots.add(fold)
            stack.addLast(OpenFold(fold, endToken))
        } catch (_: BadLocationException) {
            // ignore
        }
    }
}
