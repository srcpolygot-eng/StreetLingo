package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.ui.components.InstantIllustrationCanvas
import com.example.ui.viewmodel.HdPaintUiState
import com.example.ui.viewmodel.IllustrationUiState

@Composable
fun StreamedIllustrationPanel(
  illustrationState: IllustrationUiState,
  hdPaintState: HdPaintUiState,
  onRefresh: () -> Unit,
  onRequestHdPaint: (String) -> Unit,
  onSaveCard: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      when (illustrationState) {
        is IllustrationUiState.Idle -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Tap Illustrate to stream instant art with Gemini 4 Hydrogen",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        is IllustrationUiState.Error -> {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "Illustration Error: ${illustrationState.message}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onRefresh) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Retry Stream")
            }
          }
        }
        is IllustrationUiState.Streaming,
        is IllustrationUiState.Success -> {
          val parsed = if (illustrationState is IllustrationUiState.Streaming) {
            illustrationState.parsed
          } else {
            (illustrationState as IllustrationUiState.Success).parsed
          }
          val subject = if (illustrationState is IllustrationUiState.Streaming) {
            illustrationState.subject
          } else {
            (illustrationState as IllustrationUiState.Success).subject
          }
          val modelName = if (illustrationState is IllustrationUiState.Streaming) {
            illustrationState.modelName
          } else {
            (illustrationState as IllustrationUiState.Success).modelName
          }
          val isStreaming = illustrationState is IllustrationUiState.Streaming

          if (hdPaintState is HdPaintUiState.Success) {
            Image(
              bitmap = hdPaintState.bitmap.asImageBitmap(),
              contentDescription = "HD Studio Illustration",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp)),
            )
          } else {
            InstantIllustrationCanvas(
              parsed = parsed,
              isStreaming = isStreaming,
              modelName = modelName,
              modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            )
          }

          if (hdPaintState is HdPaintUiState.Loading) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              Text(
                text = "Painting HD Studio layer via gemini-nano-banana-2.1 (30–60s)...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          } else if (hdPaintState is HdPaintUiState.Error) {
            Text(
              text = "HD Studio Paint: ${hdPaintState.message}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }

          parsed.caption?.let { cap ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "${cap.targetPhrase} — ${cap.englishMeaning}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              if (cap.phonetic.isNotBlank()) {
                Text(
                  text = cap.phonetic,
                  style = MaterialTheme.typography.bodyMedium,
                  fontStyle = FontStyle.Italic,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilledTonalButton(
              onClick = { onRequestHdPaint(subject) },
              enabled = hdPaintState !is HdPaintUiState.Loading,
              modifier = Modifier.weight(1f).testTag("hd_paint_button"),
            ) {
              Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("HD Paint", maxLines = 1)
            }
            if (parsed.caption != null) {
              Button(
                onClick = onSaveCard,
                modifier = Modifier.weight(1f).testTag("save_illustration_card_button"),
              ) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Card", maxLines = 1)
              }
            }
          }
        }
      }
    }
  }
}
