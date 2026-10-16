package com.imparable.spoti.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imparable.spoti.data.AdBlockMode
import com.imparable.spoti.data.AdGuardMode
import com.imparable.spoti.data.GuiMode

/**
 * Ajustes. Los tres bloques de bloqueo son explícitos: el usuario debe poder ver
 * que hay **dos** capas independientes (red y neutralización) y elegir cada una.
 * Ninguno de los tres proyectos de referencia explicaba esto en su interfaz.
 */
@Composable
fun SettingsContent(state: PlayerViewModel.UiState, viewModel: PlayerViewModel) {
    val s = state.settings

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text("Bloqueo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Capa 1: red (hosts). Capa 3: neutralización dentro del reproductor. " +
                "Se combinan: la red quita rastreadores y la neutralización silencia y salta los anuncios " +
                "que llegan por la CDN compartida con la música.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Text("Filtrado de red", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdBlockMode.entries.forEach { mode ->
                FilterChip(
                    selected = s.adBlockMode == mode,
                    onClick = { viewModel.setAdBlockMode(mode) },
                    label = { Text(modeLabel(mode)) }
                )
            }
        }
        Text(
            when (s.adBlockMode) {
                AdBlockMode.OFF -> "Sin filtrado de red."
                AdBlockMode.TRACKERS -> "Solo rastreadores y telemetría. No toca ningún dominio de Spotify: no puede romper la reproducción."
                AdBlockMode.STRICT -> "Añade los endpoints publicitarios de Spotify. No incluye dominios de contenido (lista blanca con prioridad absoluta)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        Text("Neutralización de anuncios", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdGuardMode.entries.forEach { mode ->
                FilterChip(
                    selected = s.adGuardMode == mode,
                    onClick = { viewModel.setAdGuardMode(mode) },
                    label = { Text(guardLabel(mode)) }
                )
            }
        }
        Text(
            "Se prefiere NEUTRALIZAR (silencio + salto) antes que bloquear la petición: " +
                "el reproductor web detecta el bloqueo y responde congelando la reproducción.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Interfaz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Text("Modo del reproductor", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GuiMode.entries.forEach { mode ->
                FilterChip(
                    selected = s.guiMode == mode,
                    onClick = { viewModel.setGuiMode(mode) },
                    label = { Text(if (mode == GuiMode.MOBILE) "Móvil" else "Escritorio") }
                )
            }
        }
        Text(
            "Escritorio (recomendado): reproductor web de PC dentro de la app, reproduce la " +
                "lista entera y permite el aleatorio. Móvil usa el reproductor de teléfono; si " +
                "en Móvil no encadena las canciones, usa Escritorio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Barra de reproducción nativa",
            "Barra propia bajo el reproductor. Apagada porque no reflejaba bien el estado.",
            s.showNativePlayerBar
        ) { viewModel.setShowNativePlayerBar(it) }
        SwitchRow("Tema AMOLED", "Fondo negro puro en la app y en el reproductor.", s.amoled) {
            viewModel.setAmoled(it)
        }
        SwitchRow("Cerrar «Reproduciendo ahora»", "Deja más sitio al reproductor.", s.autoCloseNowPlaying) {
            viewModel.setAutoCloseNowPlaying(it)
        }
        SwitchRow("Ahorro de batería", "Reduce el sondeo del estado de 2 s a 4 s.", s.batterySaver) {
            viewModel.setBatterySaver(it)
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Diagnóstico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Capa de calibración",
            "Pinta una rejilla de medida y un rótulo con los números reales del visor " +
                "sobre la página, y el fondo del visor en magenta. Sirve para ver de un " +
                "vistazo dónde está el contenido. Solo para diagnosticar: déjala apagada " +
                "para usar la app normal. Cambia el ajuste y vuelve a abrir el reproductor."
        , s.calibration) { viewModel.setCalibration(it) }

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Modo limpio (sin inyección)",
            "La app no inyecta NADA: el visor queda como un navegador sin tocar. " +
                "Es el experimento de control: si con esto el login se ve, el culpable " +
                "es nuestro JavaScript; si sigue negro, no es nuestro código. " +
                "Cambia el ajuste y vuelve a abrir el reproductor."
        , s.cleanMode) { viewModel.setCleanMode(it) }

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Reparar elementos ocultos",
            "Spotify deja el formulario de login dentro de un contenedor que no se pinta: " +
                "la caja está bien colocada pero no se ve. Esto fuerza la visibilidad de toda " +
                "la cadena. Está activado porque sin él el login sale en negro; se puede " +
                "apagar como salida de emergencia."
        , s.unhideFix) { viewModel.setUnhideFix(it) }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Lista de bloqueo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SwitchRow("Actualizar automáticamente", "Descarga la lista al abrir la app.", s.blockListAutoUpdate) {
            viewModel.setBlockListAutoUpdate(it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            androidx.compose.material3.Button(
                onClick = { viewModel.updateBlockList() },
                enabled = !state.updatingList
            ) {
                Text(if (state.updatingList) "Actualizando…" else "Actualizar ahora")
            }
        }
        Text(
            "La lista se valida antes de aplicarla: si baja de 100 reglas o incluye dominios " +
                "críticos de Spotify, se rechaza y se conserva la anterior.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Servicio y sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SwitchRow("Detener al deslizar la app", "Para el servicio al cerrarla desde recientes.", s.swipeStop) {
            viewModel.setSwipeStop(it)
        }
        SwitchRow("Reproducir con auriculares", "Al conectarlos.", s.headphonesAutoPlay) {
            viewModel.setHeadphonesAutoPlay(it)
        }
        SwitchRow("Pausar sin auriculares", "Al desconectarlos.", s.headphonesAutoStop) {
            viewModel.setHeadphonesAutoStop(it)
        }
        SwitchRow("Reproducir con Bluetooth", "Al conectar.", s.bluetoothAutoPlay) {
            viewModel.setBluetoothAutoPlay(it)
        }
        SwitchRow("Pausar sin Bluetooth", "Al desconectar.", s.bluetoothAutoStop) {
            viewModel.setBluetoothAutoStop(it)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun modeLabel(mode: AdBlockMode) = when (mode) {
    AdBlockMode.OFF -> "Apagado"
    AdBlockMode.TRACKERS -> "Rastreadores"
    AdBlockMode.STRICT -> "Estricto"
}

private fun guardLabel(mode: AdGuardMode) = when (mode) {
    AdGuardMode.OFF -> "Apagado"
    AdGuardMode.SILENT -> "Silenciar"
    AdGuardMode.SKIP -> "Saltar"
    AdGuardMode.SILENT_AND_SKIP -> "Silenciar + saltar"
}
