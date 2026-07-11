package com.nethal.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Checkbox reaproveitado por `1a` (autorização de rede, SIG-312/313) e `1d` (opt-in de telemetria
 * beta) — o design system (`docs/design/design-system.dc.html`) não documenta um componente de
 * checkbox, então este segue a mesma linguagem visual dos demais controles (outline `Border`,
 * fill `Accent` quando marcado, [CheckGlyph] reaproveitado), em vez de duplicar a composição em
 * cada tela.
 */
@Composable
internal fun OnboardingCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) OnboardingColors.Accent else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (checked) OnboardingColors.Accent else OnboardingColors.Border,
                    shape = RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                CheckGlyph(modifier = Modifier.size(12.dp), tint = OnboardingColors.Background)
            }
        }

        Text(
            text = label,
            color = OnboardingColors.TextSecondary,
            fontSize = 12.5.sp,
        )
    }
}
