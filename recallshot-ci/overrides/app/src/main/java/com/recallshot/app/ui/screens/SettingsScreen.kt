package com.recallshot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.recallshot.app.permissions.MediaAccess
import com.recallshot.app.permissions.MediaPermissions
import com.recallshot.app.settings.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onAutoImport: (Boolean) -> Unit,
    onAutoOcr: (Boolean) -> Unit,
    onReminders: (Boolean) -> Unit,
    onRescan: () -> Unit
) {
    val access = MediaPermissions.access(LocalContext.current)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineMedium)
        SettingToggle("Importa nuovi screenshot", "Cerca automaticamente nuovi screenshot anche quando RecallShot non è in primo piano.", settings.autoImportScreenshots, onAutoImport)
        SettingToggle("Analisi OCR automatica", "Classifica gli screenshot in una coda persistente che continua in background.", settings.runOcrAutomatically, onAutoOcr)
        SettingToggle("Promemoria", "Abilita notifiche collegate agli screenshot.", settings.remindersEnabled, onReminders)

        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Accesso alle immagini", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (access) {
                            MediaAccess.FULL -> "Completo: rilevamento automatico disponibile."
                            MediaAccess.PARTIAL -> "Parziale: vengono viste solo le immagini autorizzate."
                            MediaAccess.DENIED -> "Non consentito: usa Importa oppure abilita l'accesso."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        FilledTonalButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(if (access == MediaAccess.PARTIAL) "Gestisci accesso e riscansiona" else "Scansiona di nuovo la galleria")
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Elaborazione locale attiva", style = MaterialTheme.typography.titleSmall)
                    Text("OCR, database e ricerca restano sul dispositivo.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("RecallShot non richiede un account. Le immagini condivise vengono copiate nell'area privata dell'app per non perdere l'accesso.", color = MaterialTheme.colorScheme.secondary)
        Text("Quando elimini uno screenshot acquisito dalla galleria, Android chiede conferma e l’immagine originale viene eliminata anche dalla galleria. Le immagini condivise o importate come copia privata vengono rimosse solo da RecallShot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
