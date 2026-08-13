package com.reals.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.ui.theme.RealsColors
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType

@Composable
fun RealsBrandSeal(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.ic_notification_large),
        contentDescription = null,
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clearAndSetSemantics {},
    )
}

@Composable
fun RealsScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    showSeal: Boolean = false,
    centered: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (showSeal) {
            RealsBrandSeal(modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = title,
            style = RealsType.ScreenTitle,
            color = MaterialTheme.colorScheme.primary,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        subtitle?.let {
            Text(
                text = it,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
        RealsBrandDivider(
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
fun RealsBrandDivider(
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.62f)
    Row(
        modifier = modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = color)
        Canvas(modifier = Modifier.size(8.dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
            drawPath(path, color)
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = color)
    }
}

@Composable
fun RealsEditorialSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
fun RealsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
fun RealsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(RealsRadii.Button),
    ) {
        Text(text)
    }
}

@Composable
fun RealsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(RealsRadii.Button),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
    ) {
        Text(text)
    }
}

@Composable
fun realsOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
    errorBorderColor = MaterialTheme.colorScheme.error,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun RealsArchitecturalLines(
    modifier: Modifier = Modifier,
    lightOnInk: Boolean = false,
) {
    val color = if (lightOnInk) RealsColors.SoftGold.copy(alpha = 0.18f) else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
    }
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val strokeWidth = 1.dp.toPx()
        val archTop = size.height * 0.14f
        val archBottom = size.height * 0.92f
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, archBottom),
            end = Offset(size.width * 0.18f, size.height * 0.42f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.82f, archBottom),
            end = Offset(size.width * 0.82f, size.height * 0.42f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, archTop),
            size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.56f),
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
fun RealsThinDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
