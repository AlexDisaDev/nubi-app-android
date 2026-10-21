package app.nubi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.nubi.ui.theme.NubiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NubiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaProvisional()
                }
            }
        }
    }
}

/**
 * Pantalla provisional del esqueleto. Se sustituye por la navegación real
 * (Día, Alimentación, Crecimiento, Salud, Ajustes…) en la fase 3 de la hoja
 * de ruta, portando las pantallas desde VistaLinea.swift y el resto.
 */
@Composable
fun PantallaProvisional() {
    Scaffold { padding ->
        Text(
            text = "Nubi Android — esqueleto del proyecto",
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaProvisionalPreview() {
    NubiTheme {
        PantallaProvisional()
    }
}
