package editorx.gui.compose

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import editorx.gui.theme.Theme

internal fun java.awt.Color.toComposeColor(): Color = Color(red, green, blue, alpha)

internal fun Theme.toMaterialColors(): Colors {
    val primary = primary.toComposeColor()
    val onPrimary = onPrimary.toComposeColor()
    val secondary = secondary.toComposeColor()
    val onSecondary = onSecondary.toComposeColor()
    val background = surface.toComposeColor()
    val surface = surface.toComposeColor()
    val onSurface = onSurface.toComposeColor()
    val error = error.toComposeColor()

    return when (this) {
        is Theme.Dark -> darkColors(
            primary = primary,
            primaryVariant = primaryContainer.toComposeColor(),
            secondary = secondary,
            background = background,
            surface = surface,
            error = error,
            onPrimary = onPrimary,
            onSecondary = onSecondary,
            onBackground = onSurface,
            onSurface = onSurface,
            onError = error,
        )

        is Theme.Light -> lightColors(
            primary = primary,
            primaryVariant = primaryContainer.toComposeColor(),
            secondary = secondary,
            background = background,
            surface = surface,
            error = error,
            onPrimary = onPrimary,
            onSecondary = onSecondary,
            onBackground = onSurface,
            onSurface = onSurface,
            onError = error,
        )
    }
}

