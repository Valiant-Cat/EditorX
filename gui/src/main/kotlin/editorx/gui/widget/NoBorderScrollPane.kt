package editorx.gui.widget

import javax.swing.JScrollPane
import javax.swing.JComponent

/**
 * A JScrollPane that always has no border, even after UI updates.
 * This prevents borders from reappearing when themes are switched.
 */
class NoBorderScrollPane(view: JComponent?) : JScrollPane(view) {
    
    init {
        border = null
        viewport.border = null
    }
    
    override fun updateUI() {
        super.updateUI()
        // 确保在 UI 更新后边框仍然为 null
        border = null
        viewport.border = null
    }
}
