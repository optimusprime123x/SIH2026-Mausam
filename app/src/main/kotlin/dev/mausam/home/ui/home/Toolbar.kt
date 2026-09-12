package dev.mausam.home.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.theme.Space

/**
 * The floating toolbar, built by hand so its glass pill really disappears on scroll: the pill
 * shrinks away from the FAB edge and the FAB grows from 56 to 72 dp to take its place, which is
 * the expressive collapse without a lingering container.
 */
@Composable
fun HomeToolbar(
    expanded: Boolean,
    refreshing: Boolean,
    haze: HazeState,
    onRefresh: () -> Unit,
    onLocations: () -> Unit,
    onCatalogue: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val fabSize by animateDpAsState(if (expanded) 56.dp else 72.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "fab")
    val fabCorner by animateDpAsState(if (expanded) 16.dp else 24.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "fabCorner")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.End) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.End) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            Row(
                Modifier
                    .height(64.dp)
                    .mausamGlass(haze, GlassTier.TOOLBAR, CircleShape)
                    .padding(horizontal = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolIcon(Icons.Rounded.Place, "Locations", onLocations)
                ToolIcon(Icons.Rounded.Add, "Add cards", onCatalogue)
                ToolIcon(Icons.Rounded.Settings, "Settings", onSettings)
                Spacer(Modifier.width(Space.s2))
            }
        }
        val shape = RoundedCornerShape(fabCorner)
        Box(
            Modifier
                .size(fabSize)
                .shadow(8.dp, shape, ambientColor = cs.primary.copy(alpha = 0.3f), spotColor = cs.primary.copy(alpha = 0.4f))
                .clip(shape)
                .background(Brush.linearGradient(listOf(cs.primary, cs.primaryContainer)))
                .clickable(onClick = onRefresh)
                .semantics { role = Role.Button; contentDescription = if (refreshing) "Refreshing" else "Refresh" },
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) LoadingIndicator(Modifier.size(fabSize * 0.6f), color = cs.onPrimary)
            else Icon(Icons.Rounded.Refresh, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(fabSize * 0.42f))
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface) }
}
