package com.amg.hisabkitab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.amg.hisabkitab.ui.navigation.HisabKitabApp
import com.amg.hisabkitab.ui.theme.HisabKitabTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val repository by lazy { (application as HisabKitabApplication).repository }
    private var settingsMessage by mutableStateOf<String?>(null)

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                val backup = withContext(Dispatchers.IO) { repository.createBackup() }
                contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                    requireNotNull(writer) { "Could not open the selected file" }
                    writer.write(backup)
                }
            }.onSuccess {
                settingsMessage = "Backup exported successfully."
            }.onFailure {
                settingsMessage = "Backup failed: ${it.message}"
            }
        }
    }

    private val restoreBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                        requireNotNull(reader) { "Could not read the selected file" }
                        reader.readText()
                    }
                }
                repository.restoreBackup(raw)
            }.onSuccess {
                settingsMessage = "Backup validated and restored."
            }.onFailure {
                settingsMessage = "Restore rejected: ${it.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HisabKitabTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HisabKitabApp(
                        repository = repository,
                        settingsMessage = settingsMessage,
                        onBackup = {
                            val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                            createBackup.launch("hisabkitab_backup_$date.json")
                        },
                        onRestore = { restoreBackup.launch(arrayOf("application/json", "text/plain")) },
                        onClearSettingsMessage = { settingsMessage = null }
                    )
                }
            }
        }
    }
}
