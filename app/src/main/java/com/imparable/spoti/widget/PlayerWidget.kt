package com.imparable.spoti.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.imparable.spoti.media.PlayerHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Widget del reproductor en Compose con **Glance**.
 *
 * Sustituye al widget de `RemoteViews` de Spotifuck/Spotilol: en RemoteViews hay
 * que definir el layout en XML y actualizar cada vista a mano; aquí es el mismo
 * modelo declarativo que la app, así que un cambio de diseño se hace una vez.
 *
 * Para que el widget reaccione, el estado del reproductor se mantiene en un
 * `StateFlow` global ([WidgetState]).
 */
object WidgetState {
    private val _state = MutableStateFlow(WidgetSnapshot())
    val state: StateFlow<WidgetSnapshot> = _state

    fun update(snapshot: WidgetSnapshot) {
        _state.value = snapshot
    }

    data class WidgetSnapshot(
        val title: String = "SpotiZado",
        val subtitle: String = "",
        val playing: Boolean = false,
        val favorite: Boolean = false
    )
}

class PlayerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val snapshot by WidgetState.state.collectAsState()
            WidgetContent(snapshot)
        }
    }

    @Composable
    private fun WidgetContent(snapshot: WidgetState.WidgetSnapshot) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.Black))
                .padding(12.dp)
        ) {
            Text(
                text = snapshot.title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.White)
                ),
                maxLines = 1
            )
            Text(
                text = snapshot.subtitle,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(Color.LightGray)
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(10.dp))
            Row {
                Text(
                    text = "⏮",
                    modifier = GlanceModifier.clickable(actionRunCallback<PreviousAction>()),
                    style = TextStyle(fontSize = 22.sp, color = ColorProvider(android.graphics.Color.WHITE))
                )
                Spacer(GlanceModifier.width(16.dp))
                Text(
                    text = if (snapshot.playing) "⏸" else "▶",
                    modifier = GlanceModifier.clickable(actionRunCallback<PlayPauseAction>()),
                    style = TextStyle(fontSize = 22.sp, color = ColorProvider(android.graphics.Color.WHITE))
                )
                Spacer(GlanceModifier.width(16.dp))
                Text(
                    text = "⏭",
                    modifier = GlanceModifier.clickable(actionRunCallback<NextAction>()),
                    style = TextStyle(fontSize = 22.sp, color = ColorProvider(android.graphics.Color.WHITE))
                )
                Spacer(GlanceModifier.width(16.dp))
                Text(
                    text = if (snapshot.favorite) "♥" else "♡",
                    modifier = GlanceModifier.clickable(actionRunCallback<FavoriteAction>()),
                    style = TextStyle(fontSize = 22.sp, color = ColorProvider(android.graphics.Color.WHITE))
                )
            }
        }
    }
}

/** Acciones del widget: reenvían la orden al reproductor web. */
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlayerHost.sendCommand("toggle")
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlayerHost.sendCommand("next")
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlayerHost.sendCommand("previous")
    }
}

class FavoriteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlayerHost.sendCommand("favorite")
    }
}

class PlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlayerWidget()
}
