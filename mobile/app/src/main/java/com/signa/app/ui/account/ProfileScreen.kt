package com.signa.app.ui.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.domain.model.User
import com.signa.app.domain.model.VehicleType
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaStatusLabel

@Composable
fun ProfileScreen(
    user: User, onClose: () -> Unit, onContributions: () -> Unit, onChangePassword: () -> Unit,
    isPreview: Boolean = false, errorMessage: Int? = null, isSigningOut: Boolean = false,
    vehicleType: VehicleType = user.vehicleType
) {
    var showInformation by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val initials = user.displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
    SignaPage(stringResource(R.string.nav_account)) {
        AccountHeader(initials, user.displayName, user.email, vehicleType)
        if (isPreview) SignaStatusLabel(stringResource(R.string.sample_profile_label))
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountRow(stringResource(R.string.personal_information), R.drawable.ic_account) { showInformation = true }
            AccountRow(stringResource(R.string.my_contributions), R.drawable.ic_location, onContributions)
            AccountRow(stringResource(R.string.change_password), R.drawable.ic_lock, onChangePassword)
            AccountRow(stringResource(R.string.help), R.drawable.ic_help) { showHelp = true }
        }
        errorMessage?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onClose, enabled = !isSigningOut, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Icon(painterResource(R.drawable.ic_logout), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(if (isPreview) R.string.leave_profile_preview else R.string.logout), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
    }
    if (showInformation) AlertDialog(
        onDismissRequest = { showInformation = false },
        title = { Text(stringResource(R.string.personal_information)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InformationValue(stringResource(R.string.display_name), user.displayName)
                InformationValue(stringResource(R.string.email), user.email)
                InformationValue(stringResource(R.string.vehicle_type), stringResource(vehicleLabel(vehicleType)))
            }
        },
        confirmButton = { TextButton(onClick = { showInformation = false }) { Text(stringResource(R.string.close)) } }
    )
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false }, title = { Text(stringResource(R.string.help)) },
        text = { Text(stringResource(R.string.help_account_message)) },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun InformationValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AccountRow(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
