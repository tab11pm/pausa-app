package com.tabek.mindfulpause.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tabek.mindfulpause.data.AppInfo
import com.tabek.mindfulpause.data.SUGGESTED_PACKAGES
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.AccentDeep
import com.tabek.mindfulpause.ui.theme.Divider
import com.tabek.mindfulpause.ui.theme.SurfaceGlass
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

@Composable
fun AppPicker(
    apps: List<AppInfo>,
    tracked: Set<String>,
    blockedPackages: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onOpenBlock: (String) -> Unit,
) {
    GlassCard {
        if (apps.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
            return@GlassCard
        }

        var query by rememberSaveable { mutableStateOf("") }

        // Suggested social apps that are actually installed float to the top.
        val ordered = remember(apps) {
            apps.sortedWith(
                compareByDescending<AppInfo> { it.packageName in SUGGESTED_PACKAGES }
                    .thenBy { it.label.lowercase() }
            )
        }
        // Filter by the search query (case-insensitive, matches the label).
        val visible = remember(ordered, query) {
            if (query.isBlank()) ordered
            else ordered.filter { it.label.contains(query.trim(), ignoreCase = true) }
        }

        SearchField(
            query = query,
            onQueryChange = { query = it },
        )

        if (visible.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Ничего не найдено", color = TextMuted, fontSize = 14.sp)
            }
            return@GlassCard
        }

        Column {
            visible.forEachIndexed { i, app ->
                AppRow(
                    app = app,
                    checked = app.packageName in tracked,
                    suggested = app.packageName in SUGGESTED_PACKAGES,
                    blocked = app.packageName in blockedPackages,
                    onCheckedChange = { onToggle(app.packageName, it) },
                    onLockClick = { onOpenBlock(app.packageName) },
                )
                if (i < visible.lastIndex) {
                    HorizontalDivider(
                        color = Divider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 64.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        placeholder = { Text("Поиск приложения", color = TextMuted, fontSize = 15.sp) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = TextMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Divider,
            cursorColor = Accent,
        ),
    )
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    suggested: Boolean,
    blocked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLockClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconPainter = remember(app.packageName) { app.icon?.let { IconPainter(it) } }
        if (iconPainter != null) {
            Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Box(Modifier.size(36.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (suggested) {
                Text("соцсеть", color = Accent, fontSize = 12.sp)
            }
        }
        // Lock button — opens the blocking sheet for this app.
        IconButton(onClick = onLockClick) {
            Icon(
                imageVector = if (blocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Блокировка",
                tint = if (blocked) Accent else TextMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = AccentDeep,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceGlass,
                uncheckedBorderColor = Divider,
            ),
        )
    }
}

/** Wraps a Drawable app icon as a Compose Painter. */
private class IconPainter(
    private val drawable: android.graphics.drawable.Drawable,
) : Painter() {
    private val bitmap = drawable.toBitmap(
        width = drawable.intrinsicWidth.coerceAtLeast(1),
        height = drawable.intrinsicHeight.coerceAtLeast(1),
    ).asImageBitmap()

    override val intrinsicSize = androidx.compose.ui.geometry.Size(
        bitmap.width.toFloat(),
        bitmap.height.toFloat(),
    )

    override fun DrawScope.onDraw() {
        drawImage(
            bitmap,
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}
