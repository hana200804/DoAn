package com.signa.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.signa.app.R

/** One editable field supports paste, deletion and accessibility; six boxes render its value. */
@Composable
internal fun OtpInput(value: String, error: AuthFieldError?, enabled: Boolean, onValueChange: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = stringResource(R.string.otp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
            interactionSource = interaction, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Box {
                    Box(Modifier.size(1.dp).alpha(0f)) { inner() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { index ->
                            Surface(
                                modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                                shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, when {
                                    error != null -> MaterialTheme.colorScheme.error
                                    focused && index == value.length.coerceAtMost(5) -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline
                                })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(value.getOrNull(index)?.toString() ?: "•", style = MaterialTheme.typography.titleLarge,
                                        color = if (value.getOrNull(index) == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        )
        authFieldErrorMessage(error)?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
