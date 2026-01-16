package editorx.gui.settings.compose

data class SettingsAlertRequest(
    val title: String,
    val message: String,
    val confirmText: String,
    val dismissText: String? = null,
    val onConfirm: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)

