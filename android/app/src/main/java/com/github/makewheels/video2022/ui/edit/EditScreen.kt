package com.github.makewheels.video2022.ui.edit

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var visibilityExpanded by remember { mutableStateOf(false) }

    val visibilityOptions = listOf("PUBLIC", "UNLISTED", "PRIVATE")
    val visibilityLabels = mapOf(
        "PUBLIC" to "公开",
        "UNLISTED" to "不列出",
        "PRIVATE" to "私密"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑视频") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving && !uiState.isLoading
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )

                // Visibility dropdown
                ExposedDropdownMenuBox(
                    expanded = visibilityExpanded,
                    onExpandedChange = { visibilityExpanded = it }
                ) {
                    OutlinedTextField(
                        value = visibilityLabels[uiState.visibility] ?: uiState.visibility,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("可见性") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = visibilityExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = visibilityExpanded,
                        onDismissRequest = { visibilityExpanded = false }
                    ) {
                        visibilityOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(visibilityLabels[option] ?: option) },
                                onClick = {
                                    viewModel.updateVisibility(option)
                                    visibilityExpanded = false
                                }
                            )
                        }
                    }
                }

                if (uiState.isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (uiState.isSaved) {
                    Text("保存成功", color = MaterialTheme.colorScheme.primary)
                }

                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.weight(1f))

                // Delete button
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除视频")
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个视频吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDeleted)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
