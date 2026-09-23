package rehab.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rehab.app.ui.components.RehabText
import rehab.app.ui.theme.RehabColors
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.rules.RedirectTab
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Conteneur commun à tous les éditeurs de l'écran Réglages : un `AlertDialog` stylé aux tokens
 * (DESIGN §2), avec un message d'erreur optionnel affiché sous le contenu et une action
 * supplémentaire (« Supprimer ») à gauche du bouton d'annulation. `onConfirm` ne ferme jamais le
 * dialogue lui-même : c'est `SettingsScreen.save` qui décide (Rejected laisse le dialogue ouvert).
 */
@Composable
fun EditDialog(
    title: String,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Appliquer",
    extraAction: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RehabColors.Panel,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, style = RehabText.body14.copy(fontWeight = FontWeight.W500)) },
        text = {
            Column {
                content()
                error?.let { Text(it, fontSize = 12.sp, color = RehabColors.Danger, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel.uppercase(), color = RehabColors.Accent) } },
        dismissButton = {
            Row {
                extraAction?.let { (label, action) ->
                    TextButton(onClick = action) { Text(label.uppercase(), color = RehabColors.Danger) }
                }
                TextButton(onClick = onDismiss) { Text("Annuler", color = RehabColors.Accent) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightDialog(
    day: DayOfWeek,
    current: NightWindow,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime, LocalTime) -> Unit,
) {
    var editingWakeup by remember { mutableStateOf(false) }
    val bedState = rememberTimePickerState(current.bedtime.hour, current.bedtime.minute, is24Hour = true)
    val wakeState = rememberTimePickerState(current.wakeup.hour, current.wakeup.minute, is24Hour = true)
    val colors = TimePickerDefaults.colors(
        clockDialColor = RehabColors.Bg,
        selectorColor = RehabColors.Accent,
        timeSelectorSelectedContainerColor = RehabColors.Accent,
        timeSelectorSelectedContentColor = RehabColors.Bg,
        timeSelectorUnselectedContainerColor = RehabColors.Bg,
        timeSelectorUnselectedContentColor = RehabColors.Text,
        clockDialSelectedContentColor = RehabColors.Bg,
        clockDialUnselectedContentColor = RehabColors.Text,
        containerColor = RehabColors.Panel,
    )
    fun label(h: Int, m: Int) = "%02d:%02d".format(h, m)
    EditDialog(
        title = SettingsText.dayName(day),
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(LocalTime.of(bedState.hour, bedState.minute), LocalTime.of(wakeState.hour, wakeState.minute)) },
    ) {
        Row {
            // Box + defaultMinSize : cible tactile >= 44 dp (DESIGN §2), le Text seul ne fait que
            // ~17-20 dp de haut (même remarque que SettingsGroup/KeyValueGroup).
            Box(
                Modifier.defaultMinSize(minHeight = 44.dp).clickable { editingWakeup = false }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Coucher ${label(bedState.hour, bedState.minute)}",
                    color = if (!editingWakeup) RehabColors.Accent else RehabColors.Muted,
                )
            }
            Box(
                Modifier.defaultMinSize(minHeight = 44.dp).clickable { editingWakeup = true }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Lever ${label(wakeState.hour, wakeState.minute)}",
                    color = if (editingWakeup) RehabColors.Accent else RehabColors.Muted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!editingWakeup) TimePicker(bedState, colors = colors) else TimePicker(wakeState, colors = colors)
    }
}

@Composable
fun WindowDialog(
    initial: QuotaWindow?,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var durationText by remember { mutableStateOf((initial?.duration?.toMinutes() ?: 60L).toString()) }
    var capText by remember { mutableStateOf((initial?.cap?.toMinutes() ?: 10L).toString()) }
    EditDialog(
        title = if (initial != null) "Fenêtre de quota" else "Nouvelle fenêtre",
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(durationText, capText) },
        extraAction = onDelete?.let { "Supprimer" to it },
    ) {
        OutlinedTextField(
            value = durationText,
            onValueChange = { durationText = it },
            label = { Text("Durée de la fenêtre (min)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = capText,
            onValueChange = { capText = it },
            label = { Text("Plafond (min)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

@Composable
fun NumberDialog(
    title: String,
    unit: String,
    initial: String,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    EditDialog(title = title, error = error, onDismiss = onDismiss, onConfirm = { onConfirm(value) }) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            suffix = if (unit.isNotEmpty()) ({ Text(unit) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

/**
 * Choix de l'onglet de repli de la bascule au blocage (v0.3.0) : Messages, Recherche ou Désactivée
 * (`null`, overlay comme avant). Choix unique appliqué par « Appliquer » comme les autres éditeurs ;
 * chaque option est une ligne entière cliquable d'au moins 48 dp (cible tactile >= 44 dp, DESIGN §2).
 */
@Composable
fun RedirectDialog(
    appLabel: String,
    current: RedirectTab?,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (RedirectTab?) -> Unit,
) {
    var choice by remember { mutableStateOf(current) }
    val options: List<RedirectTab?> = RedirectTab.entries + null
    EditDialog(
        title = "Bascule · $appLabel",
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(choice) },
    ) {
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .selectable(selected = choice == option, role = Role.RadioButton, onClick = { choice = option }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = choice == option,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = RehabColors.Accent, unselectedColor = RehabColors.Muted),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(SettingsText.redirectValue(option), style = RehabText.body14)
                }
            }
        }
    }
}
