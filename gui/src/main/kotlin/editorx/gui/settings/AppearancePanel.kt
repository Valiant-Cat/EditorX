package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.store.Store
import editorx.gui.core.ui.Theme
import editorx.gui.core.ui.ThemeManager
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
    private val zhButton = JRadioButton()
    private val enButton = JRadioButton()
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

        val languageGroup = ButtonGroup().apply {
            add(zhButton)
            add(enButton)
        }
        
        val themeGroup = ButtonGroup().apply {
            add(lightThemeButton)
            add(darkThemeButton)
        }

        languagePanel.layout = BoxLayout(languagePanel, BoxLayout.Y_AXIS)
        zhButton.alignmentX = LEFT_ALIGNMENT
        enButton.alignmentX = LEFT_ALIGNMENT
        zhButton.addActionListener { changeLocale(Locale.SIMPLIFIED_CHINESE) }
        enButton.addActionListener { changeLocale(Locale.ENGLISH) }
        languagePanel.add(zhButton)
        languagePanel.add(Box.createVerticalStrut(8))
        languagePanel.add(enButton)
        
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
        val locale = I18n.locale()
        val isEnglish = locale.language == Locale.ENGLISH.language
        
        // 语言设置
        if (isEnglish) {
            enButton.isSelected = true
            zhButton.text = "Chinese (Simplified)"
            enButton.text = "English"
            headerLabel.text = "Appearance"
            languagePanel.border = BorderFactory.createTitledBorder("Language")
        } else {
            zhButton.isSelected = true
            zhButton.text = "中文（简体）"
            enButton.text = "English"
            headerLabel.text = "外观"
            languagePanel.border = BorderFactory.createTitledBorder("语言")
        }
        
        // 主题设置
        val currentTheme = ThemeManager.currentTheme
        when (currentTheme) {
            is Theme.Light -> lightThemeButton.isSelected = true
            is Theme.Dark -> darkThemeButton.isSelected = true
        }
        
        if (isEnglish) {
            lightThemeButton.text = "Light"
            darkThemeButton.text = "Dark"
            themePanel.border = BorderFactory.createTitledBorder("Theme")
            footerLabel.text = "Tip: language and theme changes apply immediately."
        } else {
            lightThemeButton.text = "浅色"
            darkThemeButton.text = "深色"
            themePanel.border = BorderFactory.createTitledBorder("主题")
            footerLabel.text = "提示：语言和主题切换立即生效。"
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
        zhButton.isSelected = true
        changeLocale(Locale.SIMPLIFIED_CHINESE)
        lightThemeButton.isSelected = true
        changeTheme(Theme.Light)
        refresh()
    }

    companion object {
        private const val LOCALE_KEY = "ui.locale"
        private const val THEME_KEY = "ui.theme"
    }
}
