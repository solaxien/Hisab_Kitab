package com.amg.hisabkitab.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.amg.hisabkitab.data.local.SettingsEntity
import com.amg.hisabkitab.ui.components.HkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    message: String?,
    onBack: () -> Unit,
    onSave: (SettingsEntity) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearMessage: () -> Unit
) {
    var editProfile by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onClearMessage()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HkCard(Modifier.fillMaxWidth(), outlined = true) {
                    ListItem(
                        headlineContent = {
                            Text(settings.shopName, style = MaterialTheme.typography.titleLarge)
                        },
                        supportingContent = {
                            Column {
                                Text(settings.ownerName)
                                Text(settings.phone.ifBlank { "Add phone number" })
                                Text(settings.address.ifBlank { "Add shop address" })
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Outlined.Person, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { editProfile = true }
                    )
                }
            }
            item { SectionLabel("DATA") }
            item {
                SettingsGroup {
                    SettingRow(
                        Icons.Outlined.Backup,
                        "Backup & Export",
                        "Save a complete local backup",
                        onBackup
                    )
                    HorizontalDivider()
                    SettingRow(
                        Icons.Outlined.Restore,
                        "Restore Backup",
                        "Validate before replacing local data"
                    ) { confirmRestore = true }
                }
            }
            item { SectionLabel("PREFERENCES") }
            item {
                SettingsGroup {
                    ToggleSettingRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notifications",
                        subtitle = "Low-stock alerts",
                        checked = settings.stockNotifications,
                        onCheckedChange = {
                            onSave(settings.copy(stockNotifications = it))
                        }
                    )
                    HorizontalDivider()
                    SettingRow(Icons.Outlined.Palette, "Appearance", "Dark theme") {}
                    HorizontalDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.Lock,
                        title = "Security & PIN",
                        subtitle = "Local app privacy",
                        checked = settings.pinEnabled,
                        onCheckedChange = { onSave(settings.copy(pinEnabled = it)) }
                    )
                    HorizontalDivider()
                    SettingRow(
                        Icons.Outlined.Info,
                        "App Info & Support",
                        "HisabKitab 1.0 • Offline-first"
                    ) {}
                }
            }
        }
    }
    if (editProfile) {
        ProfileDialog(
            value = settings,
            onDismiss = { editProfile = false },
            onSave = { onSave(it); editProfile = false }
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Replace local data?") },
            text = {
                Text("The selected backup will be validated before existing records are replaced.")
            },
            confirmButton = {
                Button(onClick = { confirmRestore = false; onRestore() }) {
                    Text("Choose Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    HkCard(Modifier.fillMaxWidth(), outlined = true) { Column { content() } }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun ProfileDialog(
    value: SettingsEntity,
    onDismiss: () -> Unit,
    onSave: (SettingsEntity) -> Unit
) {
    var shop by remember { mutableStateOf(value.shopName) }
    var owner by remember { mutableStateOf(value.ownerName) }
    var phone by remember { mutableStateOf(value.phone) }
    var address by remember { mutableStateOf(value.address) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shop Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shop, { shop = it },
                    label = { Text("Shop name") },
                    isError = shop.isBlank(),
                    supportingText = if (shop.isBlank()) ({ Text("Required") }) else null
                )
                OutlinedTextField(
                    owner, { owner = it },
                    label = { Text("Owner name") },
                    isError = owner.isBlank(),
                    supportingText = if (owner.isBlank()) ({ Text("Required") }) else null
                )
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") })
                OutlinedTextField(address, { address = it }, label = { Text("Address") })
            }
        },
        confirmButton = {
            Button(
                enabled = shop.isNotBlank() && owner.isNotBlank(),
                onClick = {
                    onSave(
                        value.copy(
                            shopName = shop.trim(),
                            ownerName = owner.trim(),
                            phone = phone.trim(),
                            address = address.trim()
                        )
                    )
                }
            ) { Text("Save Profile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
