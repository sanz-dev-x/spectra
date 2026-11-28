package com.spectra.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.spectra.app.R

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spectra_whatsnew_title)) },
        text = { Text(stringResource(R.string.spectra_whatsnew_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.spectra_whatsnew_ok)) }
        },
    )
}
