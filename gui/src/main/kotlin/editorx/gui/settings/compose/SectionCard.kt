package editorx.gui.settings.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(
    title: String,
    outline: androidx.compose.ui.graphics.Color,
    titleColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, outline, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(title, color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

