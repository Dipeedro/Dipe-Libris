package com.example.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.model.BibliographicMath
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun IsbnScannerScreen(
  widthClass: WindowWidthClass,
  onIsbnDetected: (String) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
    }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      runCatching { permissionLauncher.launch(Manifest.permission.CAMERA) }
    }
  }

  var torchEnabled by remember { mutableStateOf(false) }
  var detectedIsbn by remember { mutableStateOf<String?>(null) }
  var cameraBindFailed by remember { mutableStateOf(false) }
  var manualIsbnInput by remember { mutableStateOf("") }
  var manualError by remember { mutableStateOf(false) }

  val deliverIsbn: (String) -> Unit = { raw ->
    val parsed = BibliographicMath.parseIsbn(raw)
    val resolved = parsed?.isbn13 ?: parsed?.isbn10 ?: BibliographicMath.cleanIsbn(raw)
    if (resolved.length >= 10 && detectedIsbn == null) {
      detectedIsbn = resolved
      onIsbnDetected(resolved)
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.widthIn(max = 720.dp)
          .fillMaxSize()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md)
          .testTag("isbn_scanner_screen"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
    ) {
      EditorialSubscreenTopBar(
        title = stringResource(R.string.scanner_title),
        backButtonTag = "scanner_back_button",
        onNavigateBack = onNavigateBack,
        showBottomDivider = true,
        trailingContent = {
          if (hasCameraPermission && !cameraBindFailed) {
            IconButton(
              onClick = { torchEnabled = !torchEnabled },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_torch_button"),
            ) {
              Icon(
                imageVector =
                  if (torchEnabled) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                contentDescription = stringResource(R.string.scanner_torch_toggle),
              )
            }
          }
        },
      )

      if (hasCameraPermission && !cameraBindFailed) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(280.dp)
              .clip(MaterialTheme.shapes.large)
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .testTag("scanner_camera_preview"),
          contentAlignment = Alignment.Center,
        ) {
          CameraBarcodePreview(
            torchEnabled = torchEnabled,
            onBarcodeRecognized = { rawValue ->
              val parsed = BibliographicMath.parseIsbn(rawValue)
              if (parsed != null) {
                deliverIsbn(parsed.isbn13 ?: parsed.isbn10 ?: rawValue)
              }
            },
            onCameraError = { cameraBindFailed = true },
          )

          Box(
            modifier =
              Modifier.fillMaxWidth(0.76f)
                .height(120.dp)
                .border(
                  border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
                  shape = MaterialTheme.shapes.medium,
                ),
          )

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(DipeSpacing.md),
          ) {
            Text(
              text =
                detectedIsbn?.let { stringResource(R.string.scanner_detected_isbn, it) }
                  ?: stringResource(R.string.scanner_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
            )
          }
        }
      } else {
        EditorialSurfaceCard(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          contentPadding = PaddingValues(DipeSpacing.lg),
          modifier = Modifier.testTag("scanner_permission_card"),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            ) {
              Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = stringResource(R.string.scanner_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = stringResource(R.string.scanner_permission_body),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasCameraPermission) {
              OutlinedButton(
                onClick = { runCatching { permissionLauncher.launch(Manifest.permission.CAMERA) } },
                shape = CircleShape,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("scanner_permission_button"),
              ) {
                Text(stringResource(R.string.scanner_permission_grant))
              }
            }
          }
        }
      }

      if (detectedIsbn != null) {
        OutlinedButton(
          onClick = { detectedIsbn = null },
          shape = CircleShape,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_retry_button"),
        ) {
          Icon(imageVector = Icons.Outlined.QrCodeScanner, contentDescription = null)
          Spacer(modifier = Modifier.width(DipeSpacing.xs))
          Text(stringResource(R.string.scanner_retry_action))
        }
      }

      EditorialSurfaceCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentPadding = PaddingValues(DipeSpacing.lg),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          Text(
            text = stringResource(R.string.scanner_manual_isbn_label),
            style = LocalDipeTypography.current.editorialKicker,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = manualIsbnInput,
            onValueChange = {
              manualIsbnInput = it
              manualError = false
            },
            placeholder = { Text("9788535914849") },
            isError = manualError,
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Search,
              ),
            keyboardActions =
              KeyboardActions(
                onSearch = {
                  val parsed = BibliographicMath.parseIsbn(manualIsbnInput)
                  if (parsed != null) {
                    onIsbnDetected(parsed.isbn13 ?: parsed.isbn10 ?: manualIsbnInput.trim())
                  } else {
                    manualError = true
                  }
                }
              ),
            shape = MaterialTheme.shapes.medium,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = Color.Transparent,
              ),
            modifier = Modifier.fillMaxWidth().testTag("scanner_manual_isbn_input"),
          )
          if (manualError) {
            Text(
              text = stringResource(R.string.book_validation_isbn),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("scanner_manual_isbn_error"),
            )
          }
          Button(
            onClick = {
              val parsed = BibliographicMath.parseIsbn(manualIsbnInput)
              if (parsed != null) {
                onIsbnDetected(parsed.isbn13 ?: parsed.isbn10 ?: manualIsbnInput.trim())
              } else {
                manualError = true
              }
            },
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("scanner_manual_isbn_submit"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(DipeSpacing.xs))
            Text(stringResource(R.string.scanner_manual_isbn_submit))
          }
        }
      }
    }
  }
}

@Composable
private fun CameraBarcodePreview(
  torchEnabled: Boolean,
  onBarcodeRecognized: (String) -> Unit,
  onCameraError: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val cameraSetup =
    remember(context) {
      runCatching {
        val options =
          BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
              Barcode.FORMAT_EAN_13,
              Barcode.FORMAT_EAN_8,
              Barcode.FORMAT_UPC_A,
            )
            .build()
        val scanner = BarcodeScanning.getClient(options)
        val controller = LifecycleCameraController(context)
        controller.setImageAnalysisAnalyzer(
          ContextCompat.getMainExecutor(context),
          MlKitAnalyzer(
            listOf(scanner),
            CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context),
          ) { result ->
            val barcodes = result.getValue(scanner).orEmpty()
            for (barcode in barcodes) {
              val raw = barcode.rawValue ?: continue
              val parsed = BibliographicMath.parseIsbn(raw)
              if (parsed != null) {
                onBarcodeRecognized(parsed.isbn13 ?: parsed.isbn10 ?: raw)
                break
              }
            }
          },
        )
        scanner to controller
      }.onFailure { onCameraError() }.getOrNull()
    }

  val controller = cameraSetup?.second
  LaunchedEffect(controller, torchEnabled) {
    runCatching { controller?.enableTorch(torchEnabled) }
  }

  DisposableEffect(lifecycleOwner, cameraSetup) {
    if (cameraSetup != null) {
      runCatching { cameraSetup.second.bindToLifecycle(lifecycleOwner) }
        .onFailure { onCameraError() }
    }
    onDispose {
      runCatching { cameraSetup?.second?.unbind() }
      runCatching { cameraSetup?.first?.close() }
    }
  }

  if (controller != null) {
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          scaleType = PreviewView.ScaleType.FILL_CENTER
          this.controller = controller
        }
      },
      modifier = Modifier.fillMaxSize(),
    )
  }
}
