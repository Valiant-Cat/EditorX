package editorx.core.i18n

import java.util.Locale

/**
 * 翻译提供器：返回指定 Locale 下的 key -> 文案 映射。
 *
 * 约定：
 * - 返回的 Map 应尽量是不可变的；
 * - key 建议使用点分层级（如 "menu.file" / "action.find"）。
 */
fun interface TranslationProvider {
    fun translations(locale: Locale): Map<String, String>
}

