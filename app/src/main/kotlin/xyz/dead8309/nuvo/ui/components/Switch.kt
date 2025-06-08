package xyz.dead8309.nuvo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun Switch(
    modifier: Modifier = Modifier,
    checked: Boolean,
    enable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .requiredSize(56.dp, 28.dp)
            .alpha(if (enable) 1f else 0.5f),
        shape = CircleShape,
        color = animateColorAsState(
            with(MaterialTheme.colorScheme) {
                if (checked) primary
                else surfaceVariant
            }
        ).value
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                    then if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ) {
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = animateDpAsState(if (checked) 32.dp else 4.dp).value),
                shape = CircleShape,
                color = animateColorAsState(
                    with(MaterialTheme.colorScheme) {
                        if (checked) onPrimary
                        else outline
                    }
                ).value
            ) {}
        }
    }
}