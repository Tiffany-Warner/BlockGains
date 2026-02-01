package com.tiffles.blockgains.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvUploadScreen(
    onFilesSelected: (buysUri: Uri, sellsUri: Uri) -> Unit
) {
    var buysUri by remember { mutableStateOf<Uri?>(null) }
    var sellsUri by remember { mutableStateOf<Uri?>(null) }

    val buysLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        buysUri = uri
    }

    val sellsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        sellsUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BlockGains") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            // Header
            Text(
                text = "Upload Transaction Files",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Upload CSV files containing your buy and sell transactions to calculate capital gains",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Buy transactions card
            FileUploadCard(
                title = "Buy Transactions",
                description = "Columns: Date bought, Quantity, Purchase price per unit",
                fileName = buysUri?.lastPathSegment,
                onUploadClick = { buysLauncher.launch("text/*") }
            )

            // Sell transactions card
            FileUploadCard(
                title = "Sell Transactions",
                description = "Columns: Date sold, Quantity Sold, Sale price per unit",
                fileName = sellsUri?.lastPathSegment,
                onUploadClick = { sellsLauncher.launch("text/*") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Calculate button
            Button(
                onClick = {
                    if (buysUri != null && sellsUri != null) {
                        onFilesSelected(buysUri!!, sellsUri!!)
                    }
                },
                enabled = buysUri != null && sellsUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Calculate Capital Gains",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun FileUploadCard(
    title: String,
    description: String,
    fileName: String?,
    onUploadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (fileName != null) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "File uploaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (fileName != null) {
                Text(
                    text = "Selected: $fileName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = onUploadClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (fileName != null) "Change File" else "Choose File")
            }
        }
    }
}
