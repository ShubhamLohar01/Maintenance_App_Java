package com.candorfoods.factoryops.presentation.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle

/**
 * Add/edit sheet over the mt_users columns. When [existing] is null it's an add form;
 * otherwise the fields pre-fill for an edit. Builds an MtUser and hands it to [onSubmit]
 * (the ViewModel decides POST vs PUT). No password field — new users share the fixed login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFormSheet(
    existing: MtUser?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (MtUser) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var empId by remember { mutableStateOf(existing?.empId ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var contactNo by remember { mutableStateOf(existing?.contactNo ?: "") }
    var emailId by remember { mutableStateOf(existing?.emailId ?: "") }
    var role by remember { mutableStateOf(existing?.role?.uppercase() ?: UserRole.OPERATOR.name) }

    val emailValid = emailId.isBlank() ||
        Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(emailId.trim())
    val canSubmit = name.isNotBlank() && username.isNotBlank() && emailValid && !isSaving

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (existing == null) "Add new user" else "Edit user",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Field("Name *", name) { name = it }
            Field("Username *", username) { username = it.trim() }
            Text(
                "Username is the login id (stored in lowercase). New users sign in with the shared password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            RoleDropdown(role) { role = it }
            Field("Employee ID", empId) { empId = it }
            Field("Location", location) { location = it }
            Field("Contact no.", contactNo, keyboardType = KeyboardType.Phone) { contactNo = it }
            Field("Email", emailId, keyboardType = KeyboardType.Email) { emailId = it }
            if (!emailValid) {
                Text(
                    "Enter a valid email or leave it blank.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = if (existing == null) "Add user" else "Save changes",
                onClick = {
                    onSubmit(
                        MtUser(
                            id = existing?.id ?: 0,
                            name = name.trim(),
                            username = username.trim().lowercase(),
                            empId = empId.trim().ifBlank { null },
                            location = location.trim().ifBlank { null },
                            contactNo = contactNo.trim().ifBlank { null },
                            emailId = emailId.trim().ifBlank { null },
                            role = role,
                        )
                    )
                },
                enabled = canSubmit,
                loading = isSaving,
                style = PrimaryButtonStyle.Primary,
                size = PrimaryButtonSize.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                Text("Cancel")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Role *") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UserRole.values().forEach { r ->
                DropdownMenuItem(text = { Text(r.name) }, onClick = { onSelect(r.name); expanded = false })
            }
        }
    }
}
