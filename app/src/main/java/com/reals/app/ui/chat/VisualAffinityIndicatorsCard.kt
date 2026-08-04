package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.VisualAffinityIndicator

internal fun affinityIndicatorsForDisplay(
    indicators: List<VisualAffinityIndicator>,
): List<VisualAffinityIndicator> {
    val seenCategoryIds = mutableSetOf<String>()
    return indicators
        .asSequence()
        .filter { it.categoryId.isNotBlank() }
        .filter { it.title.isNotBlank() }
        .filter { seenCategoryIds.add(it.categoryId) }
        .take(3)
        .toList()
}

@Composable
internal fun VisualAffinityIndicatorsCard(
    indicators: List<VisualAffinityIndicator>,
    modifier: Modifier = Modifier,
) {
    val visibleIndicators = affinityIndicatorsForDisplay(indicators)
    if (visibleIndicators.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Afinidades compartidas", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Temas en los que encontraron afinidad.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleIndicators.forEach { indicator ->
                    VisualAffinityIndicatorPill(indicator)
                }
            }
        }
    }
}

@Composable
private fun VisualAffinityIndicatorPill(indicator: VisualAffinityIndicator) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = TextSafety.safeDisplay(indicator.title, maxLength = 100),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
