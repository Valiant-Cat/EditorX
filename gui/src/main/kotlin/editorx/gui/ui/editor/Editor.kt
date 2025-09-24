package editorx.gui.ui.editor

import editorx.gui.ui.MainWindow
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import javax.swing.*

class Editor(private val mainWindow: MainWindow) : JTabbedPane() {
    private val fileToTab = mutableMapOf<File, Int>()
    private val tabToFile = mutableMapOf<Int, File>()

    init {
        tabPlacement = JTabbedPane.TOP
        tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
    }

    fun openFile(file: File) {
        if (fileToTab.containsKey(file)) { selectedIndex = fileToTab[file]!!; return }
        val textArea = RSyntaxTextArea().apply {
            syntaxEditingStyle = detectSyntax(file)
            font = Font("Consolas", Font.PLAIN, 14)
        }
        try {
            textArea.text = Files.readString(file.toPath())
            textArea.discardAllEdits()
        } catch (e: Exception) {
            textArea.text = "无法读取文件: ${e.message}"
            textArea.isEditable = false
        }
        val scroll = RTextScrollPane(textArea)
        val title = file.name
        addTab(title, null, scroll, null)
        val index = tabCount - 1
        fileToTab[file] = index
        tabToFile[index] = file
        val closeButton = createCloseButton(file, index)
        setTabComponentAt(index, closeButton)
        selectedIndex = index
    }

    private fun createCloseButton(file: File, index: Int): JPanel = JPanel().apply {
        layout = java.awt.BorderLayout(); isOpaque = false
        val label = JLabel(file.name).apply { border = BorderFactory.createEmptyBorder(0, 0, 0, 5) }
        add(label, java.awt.BorderLayout.CENTER)
        val closeLabel = JLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 14f)
            foreground = Color.GRAY
            preferredSize = Dimension(16, 16)
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { foreground = Color.RED; cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR) }
                override fun mouseExited(e: MouseEvent) { foreground = Color.GRAY; cursor = java.awt.Cursor.getDefaultCursor() }
                override fun mouseClicked(e: MouseEvent) { closeTab(index) }
            })
        }
        add(closeLabel, java.awt.BorderLayout.EAST)
    }

    private fun closeTab(index: Int) {
        if (index >= 0 && index < tabCount) {
            val file = tabToFile[index]
            removeTabAt(index)
            file?.let { fileToTab.remove(it) }
            tabToFile.remove(index)
            val newTabToFile = mutableMapOf<Int, File>()
            val newFileToTab = mutableMapOf<File, Int>()
            for (i in 0 until tabCount) {
                val f = tabToFile[i]
                if (f != null) { newTabToFile[i] = f; newFileToTab[f] = i }
            }
            tabToFile.clear(); tabToFile.putAll(newTabToFile)
            fileToTab.clear(); fileToTab.putAll(newFileToTab)
        }
    }

    private fun detectSyntax(file: File): String = when {
        file.name.endsWith(".smali") -> "text/smali"
        file.name.endsWith(".xml") -> SyntaxConstants.SYNTAX_STYLE_XML
        file.name.endsWith(".java") -> SyntaxConstants.SYNTAX_STYLE_JAVA
        file.name.endsWith(".kt") -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
        file.name.endsWith(".js") -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
        file.name.endsWith(".css") -> SyntaxConstants.SYNTAX_STYLE_CSS
        file.name.endsWith(".html") -> SyntaxConstants.SYNTAX_STYLE_HTML
        file.name.endsWith(".json") -> SyntaxConstants.SYNTAX_STYLE_JSON
        else -> SyntaxConstants.SYNTAX_STYLE_NONE
    }

    fun getCurrentFile(): File? = if (selectedIndex >= 0) tabToFile[selectedIndex] else null
    fun hasUnsavedChanges(): Boolean = false
}
