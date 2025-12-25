package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.store.Store
import editorx.gui.core.Theme
import editorx.gui.core.ThemeManager
import java.awt.BorderLayout
import java.awt.Font
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton

class AppearancePanel(private val settings: Store) : JPanel(BorderLayout()) {
    private val languageButtons = mutableMapOf<Locale, JRadioButton>()
    private val languageGroup = ButtonGroup()
    private val lightThemeButton = JRadioButton()
    private val darkThemeButton = JRadioButton()
    private val headerLabel = JLabel()
    private val footerLabel = JLabel()
    private val languagePanel = JPanel()
    private val themePanel = JPanel()

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 16f)
        headerLabel.border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
        
        // 主题按钮分组
        ButtonGroup().apply {
            add(lightThemeButton)
            add(darkThemeButton)
        }

        languagePanel.layout = BoxLayout(languagePanel, BoxLayout.Y_AXIS)
        
        themePanel.layout = BoxLayout(themePanel, BoxLayout.Y_AXIS)
        lightThemeButton.alignmentX = LEFT_ALIGNMENT
        darkThemeButton.alignmentX = LEFT_ALIGNMENT
        lightThemeButton.addActionListener { changeTheme(Theme.Light) }
        darkThemeButton.addActionListener { changeTheme(Theme.Dark) }
        themePanel.add(lightThemeButton)
        themePanel.add(Box.createVerticalStrut(8))
        themePanel.add(darkThemeButton)

        footerLabel.border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
        
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(languagePanel)
            add(Box.createVerticalStrut(24))
            add(themePanel)
        }

        add(headerLabel, BorderLayout.NORTH)
        add(mainPanel, BorderLayout.CENTER)
        add(footerLabel, BorderLayout.SOUTH)

        refresh()
    }

    fun refresh() {
        val currentLocale = I18n.locale()
        
        // 更新语言选项按钮
        updateLanguageButtons(currentLocale)
        
        // 更新界面文本
        headerLabel.text = I18n.translate(I18nKeys.Settings.APPEARANCE)
        languagePanel.border = BorderFactory.createTitledBorder(I18n.translate(I18nKeys.Settings.LANGUAGE))
        themePanel.border = BorderFactory.createTitledBorder(I18n.translate(I18nKeys.Settings.THEME))
        
        // 主题设置
        val currentTheme = ThemeManager.currentTheme
        when (currentTheme) {
            is Theme.Light -> lightThemeButton.isSelected = true
            is Theme.Dark -> darkThemeButton.isSelected = true
        }
        
        lightThemeButton.text = I18n.translate(I18nKeys.Theme.LIGHT)
        darkThemeButton.text = I18n.translate(I18nKeys.Theme.DARK)
        footerLabel.text = I18n.translate(I18nKeys.Settings.APPEARANCE_TIP)
    }

    private fun updateLanguageButtons(currentLocale: Locale) {
        val availableLocales = I18n.getAvailableLocales()
        
        // 移除旧的按钮
        languagePanel.removeAll()
        // 从 ButtonGroup 中移除所有按钮
        languageButtons.values.forEach { languageGroup.remove(it) }
        languageButtons.clear()
        
        // 创建新的按钮
        availableLocales.forEachIndexed { index, locale ->
            val button = JRadioButton().apply {
                alignmentX = LEFT_ALIGNMENT
                text = getLanguageDisplayName(locale)
                addActionListener { changeLocale(locale) }
            }
            languageButtons[locale] = button
            languageGroup.add(button)
            languagePanel.add(button)
            if (index < availableLocales.size - 1) {
                languagePanel.add(Box.createVerticalStrut(8))
            }
        }
        
        // 最后设置选中状态（必须在添加到 ButtonGroup 之后）
        languageButtons[currentLocale]?.isSelected = true
        
        languagePanel.revalidate()
        languagePanel.repaint()
    }

    private fun getLanguageDisplayName(locale: Locale): String {
        // 尝试从翻译中获取语言名称
        val langKey = I18nKeys.Lang.forLocale(locale)
        
        val translated = I18n.translate(langKey)
        // 如果翻译返回的是 key 本身，使用 Locale 的显示名称（用当前界面语言显示）
        // 这样即使新增语言包，现有语言包也不需要更新
        return if (translated == langKey) {
            val currentLocale = I18n.locale()
            locale.getDisplayName(currentLocale)
        } else {
            translated
        }
    }

    private fun changeLocale(locale: Locale) {
        if (I18n.locale() == locale) return
        I18n.setLocale(locale)
        settings.put(LOCALE_KEY, locale.toLanguageTag())
        settings.sync()
    }
    
    private fun changeTheme(theme: Theme) {
        if (ThemeManager.currentTheme == theme) return
        ThemeManager.currentTheme = theme
        settings.put(THEME_KEY, ThemeManager.getThemeName(theme))
        settings.sync()
    }

    fun resetToDefault() {
        val defaultLocale = Locale.SIMPLIFIED_CHINESE
        if (I18n.getAvailableLocales().contains(defaultLocale)) {
            changeLocale(defaultLocale)
        } else {
            // 如果没有简体中文，使用第一个可用语言
            I18n.getAvailableLocales().firstOrNull()?.let { changeLocale(it) }
        }
        lightThemeButton.isSelected = true
        changeTheme(Theme.Light)
        refresh()
    }

    companion object {
        private const val LOCALE_KEY = "ui.locale"
        private const val THEME_KEY = "ui.theme"
    }
}
