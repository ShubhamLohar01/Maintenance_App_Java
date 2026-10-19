package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Top-bar "download this section's records as Excel" button.
 *
 * Shared so every Daily Reading section downloads the same way and reads the same way —
 * a spinner in place of the icon while the file is being written, and disabled when there
 * is nothing to export (an .xlsx of nothing but headers looks like a bug).
 */
@Composable
fun DownloadAction(
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (isDownloading) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
    } else {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(Icons.Filled.Download, contentDescription = "Download as Excel")
        }
    }
}
