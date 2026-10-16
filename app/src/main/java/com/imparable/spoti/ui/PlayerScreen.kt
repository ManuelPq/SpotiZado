package com.imparable.spoti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imparable.spoti.BuildConfig

/**
 * Pantalla principal.
 *
 * El reproductor ocupa el centro; abajo, el estado y los controles. Cuando el
 * reproductor no responde se explica **qué** falla en lugar de dejar una pantalla
 * negra: ese diagnóstico visible es la diferencia práctica frente a los tres
 * proyectos de referencia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerViewModel.UiState,
    viewModel: PlayerViewModel,
    playerSlot: @Composable () -> Unit
) {
    var sheet by remember { mutableStateOf(Sheet.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SpotiZado", fontWeight = FontWeight.Bold)
                        Text(
                            text = state.snapshot.track ?: "Sin reproducción",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { sheet = Sheet.SEARCH }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = {
                        viewModel.refreshQueue()
                        sheet = Sheet.QUEUE
                    }) {
                        Icon(Icons.Default.List, contentDescription = "Cola")
                    }
                    IconButton(onClick = { sheet = Sheet.DIAGNOSTICS }) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnóstico")
                    }
                    IconButton(onClick = { sheet = Sheet.SETTINGS }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                playerSlot()
            }

            // El aviso va **fuera** de la caja del reproductor, como hermano
            // debajo. Antes se dibujaba encima del WebView: un aviso que dice
            // «usa email + contraseña» tapando el formulario de email y
            // contraseña, y con los botones sin responder al toque porque el
            // WebView se quedaba la pulsación. Aquí no puede pasar ninguna de las dos.
            PlayerNotice(
                state = state,
                viewModel = viewModel,
                onOpenDiagnostics = { sheet = Sheet.DIAGNOSTICS }
            )

            NowPlayingBar(state, viewModel)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (sheet) {
        Sheet.SEARCH -> ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.NONE },
            sheetState = sheetState
        ) {
            SearchSheet(state, viewModel) { sheet = Sheet.NONE }
        }

        Sheet.QUEUE -> ModalBottomSheet(
            onDismissRequest = {
                viewModel.closeQueuePanel()
                sheet = Sheet.NONE
            },
            sheetState = sheetState
        ) {
            QueueSheet(state, viewModel)
        }

        Sheet.SETTINGS -> ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.NONE },
            sheetState = sheetState
        ) {
            SettingsContent(state, viewModel)
        }

        Sheet.DIAGNOSTICS -> ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.NONE },
            sheetState = sheetState
        ) {
            DiagnosticsContent(state, viewModel)
        }

        Sheet.NONE -> Unit
    }
}

enum class Sheet { NONE, SEARCH, QUEUE, SETTINGS, DIAGNOSTICS }

/* ---------------------------------------------------------------------- */
/* Búsqueda                                                                */
/* ---------------------------------------------------------------------- */

@Composable
private fun SearchSheet(
    state: PlayerViewModel.UiState,
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf(state.search.query) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Buscar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "La búsqueda se ejecuta en el propio reproductor con tu sesión: " +
                "la app no guarda ni usa credenciales de Spotify.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Canción, álbum, artista…") },
            trailingIcon = {
                if (state.search.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { viewModel.search(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { viewModel.search(query) }
            )
        )

        state.search.reason?.let { reason ->
            Spacer(Modifier.height(10.dp))
            AssistChip(onClick = { }, label = { Text(reason, style = MaterialTheme.typography.labelSmall) })
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.search.items) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.playResult(result)
                            onClose()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(model = result.image, size = 44)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            result.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOf(result.kindLabel, result.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------------------------------------------------------------------- */
/* Cola                                                                    */
/* ---------------------------------------------------------------------- */

@Composable
private fun QueueSheet(state: PlayerViewModel.UiState, viewModel: PlayerViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cola de reproducción", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = { viewModel.refreshQueue() }, label = { Text("Actualizar") })
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Es la cola del propio reproductor: la app la lee y te deja saltar a un " +
                "elemento, pero no la duplica (así no se desincroniza).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        state.queue.reason?.let { reason ->
            Spacer(Modifier.height(10.dp))
            AssistChip(onClick = { }, label = { Text(reason, style = MaterialTheme.typography.labelSmall) })
        }

        Spacer(Modifier.height(8.dp))
        if (state.queue.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.queue.items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.playQueueItem(item)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${item.index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(10.dp))
                    Cover(model = item.image, size = 40)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------------------------------------------------------------------- */
/* Diagnóstico                                                             */
/* ---------------------------------------------------------------------- */

@Composable
private fun DiagnosticsContent(state: PlayerViewModel.UiState, viewModel: PlayerViewModel) {
    val stats = state.stats

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Diagnóstico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        DiagnosticRow("Modo de red", stats.mode.name)
        DiagnosticRow("Modo de neutralización", stats.guardMode.name)
        DiagnosticRow("Reglas cargadas", stats.rulesLoaded.toString())
        DiagnosticRow("Versión de lista", stats.rulesVersion)
        DiagnosticRow("Bloqueos (sesión)", stats.blockedSinceStart.toString())
        DiagnosticRow("Bloqueos (total)", stats.blockedTotal.toString())
        DiagnosticRow("Anuncios neutralizados", stats.adsNeutralized.toString())
        DiagnosticRow("Último evento de anuncio", state.lastAdPhase ?: "—")
        DiagnosticRow("Versión de la app", com.imparable.spoti.BuildConfig.VERSION_NAME)
        DiagnosticRow("Puente soportado", if (state.health.ready || state.health.playButtonPresent) "sí" else "—")
        DiagnosticRow("Botón de play presente", if (state.health.playButtonPresent) "sí" else "no")
        DiagnosticRow("Temporizador reproducción", formatCountdown(state.sleepRemainingMs))
        DiagnosticRow("Apagado automático", formatCountdown(state.shutRemainingMs))
        DiagnosticRow("Elementos de audio/vídeo", state.health.mediaElements.toString())
        DiagnosticRow("Campos en la página", state.health.inputCount.toString())
        DiagnosticRow("Campo de contraseña", if (state.health.passwordField) "sí" else "no")
        DiagnosticRow("Contraseña VISIBLE", if (state.health.passwordVisible) "sí" else "no")
        DiagnosticRow(
            "Vista / documento",
            "${state.health.viewportWidth}×${state.health.viewportHeight} / ${state.health.scrollWidth}"
        )
        DiagnosticRow("Escala", state.health.pageScale.toString())
        DiagnosticRow("Botón de login", if (state.health.submitButton) "sí" else "no")
        DiagnosticRow("Texto visible", state.health.bodyLength.toString())
        DiagnosticRow("device_id", state.health.device ?: "—")
        DiagnosticRow("URL", state.health.url ?: "—")

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { viewModel.refreshStats() }, label = { Text("Actualizar") })
            AssistChip(onClick = { viewModel.resetCounters() }, label = { Text("Reiniciar contadores") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            AssistChip(onClick = { viewModel.reloadPlayer() }, label = { Text("Recargar reproductor") })
            AssistChip(onClick = { viewModel.refreshQueue() }, label = { Text("Comprobar cola") })
        }

        state.listMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun formatCountdown(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/* ---------------------------------------------------------------------- */
/* Piezas comunes                                                          */
/* ---------------------------------------------------------------------- */

/**
 * Carátula.
 *
 * Limitación conocida: el cargador de imágenes (Coil 3.6) exige compilar contra
 * la API 37, que todavía no está publicada en el SDK de este entorno, así que
 * por ahora se reserva el hueco con el tamaño correcto — la lista no "salta" y
 * sustituirlo por una imagen real es un cambio local a este composable.
 */
@Composable
private fun Cover(model: String?, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            Text(
                "♪",
                fontSize = (size / 2).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Acción que ofrece el aviso. */
private enum class NoticeAction { NONE, LOGIN, DIAGNOSTICS }

private data class Notice(
    val title: String,
    val detail: String,
    val action: NoticeAction = NoticeAction.NONE,
    val showProgress: Boolean = false
)

private fun noticeFor(state: PlayerViewModel.UiState): Notice? = when {
    !state.settings.serviceEnabled ->
        Notice("Servicio detenido", "Actívalo en Ajustes para cargar el reproductor.", NoticeAction.DIAGNOSTICS)

    state.health.url?.contains("accounts.spotify.com") == true ->
        if (state.health.passwordField) {
            // El formulario ya está en pantalla: aquí el aviso solo debe decir
            // qué hacer, no cómo llegar al formulario.
            Notice(
                "Inicia sesión con tu cuenta",
                "Escribe tu email y contraseña. El botón de Google no funciona dentro de " +
                    "los WebView. La app no guarda credenciales.",
                NoticeAction.LOGIN
            )
        } else {
            Notice(
                "Falta iniciar sesión",
                "Si no ves el formulario, pulsa «Abrir login con email».",
                NoticeAction.LOGIN
            )
        }

    // Sesión no detectada y sin reproductor a la vista (la portada pública de
    // open.spotify.com): también conviene guiar al login.
    state.health.url?.contains("open.spotify.com") == true &&
        !state.health.hasToken && !state.health.playButtonPresent && state.snapshot.isEmpty ->
        Notice(
            "Inicia sesión para escuchar",
            "El reproductor funciona con tu propia cuenta de Spotify; la app no guarda credenciales.",
            NoticeAction.LOGIN
        )

    !state.health.hasToken && !state.health.ready ->
        Notice(
            "Iniciando el reproductor…",
            "Si se queda aquí, comprueba que Android System WebView está actualizado.",
            NoticeAction.NONE,
            showProgress = true
        )

    else ->
        Notice("Reproductor degradado", "Abre el diagnóstico para ver qué capa está fallando.", NoticeAction.DIAGNOSTICS)
}

/**
 * Aviso del reproductor.
 *
 * Es una **banda descartable**, no una capa a pantalla completa: en la primera
 * versión el aviso cubría al 85 % la propia página de login, así que el usuario
 * leía «usa email + contraseña» con el formulario justo debajo, tapado. Y no
 * ofrecía ninguna acción: el aviso decía dónde ir pero no llevaba.
 */
@Composable
private fun PlayerNotice(
    state: PlayerViewModel.UiState,
    viewModel: PlayerViewModel,
    onOpenDiagnostics: () -> Unit
) {
    if (state.playerOperational) return

    val notice = noticeFor(state) ?: return
    var dismissedTitle by remember { mutableStateOf<String?>(null) }
    if (dismissedTitle == notice.title) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        notice.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(notice.detail, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { dismissedTitle = notice.title }) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar aviso")
                }
            }

            // Estado real sobre la pantalla. Sin esto, diagnosticar a distancia
            // depende de que el usuario encuentre el panel de Diagnóstico y lea
            // las filas correctas; con esto basta con mirar el aviso.
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("v").append(BuildConfig.VERSION_NAME)
                    append(" · campos=").append(state.health.inputCount)
                    append(" · pwd=").append(if (state.health.passwordField) "sí" else "no")
                    append(" · visible=").append(if (state.health.passwordVisible) "sí" else "no")
                    append(" · vw=").append(state.health.viewportWidth)
                    append(" · sw=").append(state.health.scrollWidth)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.health.url?.let { url ->
                Text(
                    text = url.take(72),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (notice.action) {
                    NoticeAction.LOGIN -> {
                        Button(onClick = { viewModel.openLogin() }) {
                            Text("Abrir login con email")
                        }
                        AssistChip(
                            onClick = { viewModel.openLoginInBrowser() },
                            label = { Text("Probar en el navegador") }
                        )
                    }

                    NoticeAction.DIAGNOSTICS -> {
                        AssistChip(onClick = onOpenDiagnostics, label = { Text("Abrir diagnóstico") })
                        AssistChip(onClick = { viewModel.reloadPlayer() }, label = { Text("Recargar") })
                    }

                    NoticeAction.NONE ->
                        AssistChip(onClick = { viewModel.reloadPlayer() }, label = { Text("Recargar") })
                }
            }

            if (notice.showProgress) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Tarjeta de reproducción + controles. */
@Composable
private fun NowPlayingBar(state: PlayerViewModel.UiState, viewModel: PlayerViewModel) {
    val snap = state.snapshot
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(model = snap.cover, size = 52)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        snap.track ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        snap.artist.ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (snap.adPlaying) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Anuncio neutralizado", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (snap.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito"
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Controles de transporte. Se usan glifos de texto para no depender de
            // material-icons-extended (que añade ~4 MB y está en desuso).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Text("⤨", fontSize = 20.sp)
                }
                IconButton(onClick = viewModel::previous) {
                    Text("⏮", fontSize = 22.sp)
                }
                IconButton(onClick = viewModel::togglePlayPause) {
                    Text(if (snap.playing) "⏸" else "▶", fontSize = 26.sp)
                }
                IconButton(onClick = viewModel::next) {
                    Text("⏭", fontSize = 22.sp)
                }
                IconButton(onClick = viewModel::toggleRepeat) {
                    Text(
                        when (snap.repeatIcon) {
                            com.imparable.spoti.data.PlayerSnapshot.RepeatMode.ONE -> "🔂"
                            com.imparable.spoti.data.PlayerSnapshot.RepeatMode.ALL -> "🔁"
                            else -> "⇄"
                        },
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
