package info.plateaukao.einkbro.proxy

import android.app.Activity
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.core.mihomo.api.ProxyGroup
import info.plateaukao.einkbro.core.mihomo.profile.ProfileRecord
import info.plateaukao.einkbro.preference.ProxyTransportMode
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProxySettingsScreen(
    viewModel: ProxyViewModel,
    onOpenDashboard: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProfileRecord?>(null) }
    var nodePickerGroup by remember { mutableStateOf<ProxyGroup?>(null) }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.setTransportMode(ProxyTransportMode.STRICT_VPN)
        } else {
            viewModel.reportVpnPermissionDenied()
        }
    }

    val profilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val yaml = withContext(Dispatchers.IO) {
                    readProfile(context.contentResolver, uri)
                }
                val name = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.')
                    .orEmpty()
                    .ifBlank { context.getString(R.string.proxy_local_profile) }
                viewModel.importLocal(name, yaml)
            } catch (error: Throwable) {
                viewModel.reportExternalError(error, ProxyErrorCategory.PROFILE)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RuntimeStatusCard(
                state = state,
                onRetry = viewModel::retryProxy,
                onDirectOnce = viewModel::useDirectOnce,
            )
        }
        item {
            EnableProxyCard(state = state, onEnabledChange = viewModel::setEnabled)
        }
        item {
            TransportCard(
                state = state,
                onSelectBrowser = {
                    viewModel.setTransportMode(ProxyTransportMode.BROWSER_PROXY)
                },
                onSelectStrictVpn = {
                    val permission = VpnService.prepare(context)
                    if (permission == null) {
                        viewModel.setTransportMode(ProxyTransportMode.STRICT_VPN)
                    } else {
                        vpnPermission.launch(permission)
                    }
                },
            )
        }
        item {
            FailClosedCard(state = state, onCheckedChange = viewModel::setFailClosed)
        }

        state.error?.let { error ->
            item {
                ProxyErrorCard(error = error, onDismiss = viewModel::clearError)
            }
        }

        item {
            ProfileSection(
                state = state,
                onImport = {
                    profilePicker.launch(
                        arrayOf("text/*", "application/yaml", "application/octet-stream")
                    )
                },
                onAddSubscription = { showSubscriptionDialog = true },
                onSelect = viewModel::activate,
                onRefresh = viewModel::refreshSubscription,
                onDeleteRequest = { pendingDelete = it },
            )
        }

        if (state.enabled && state.activeProfileId.isNotBlank()) {
            item {
                RoutingModeCard(state = state, onMode = viewModel::setRoutingMode)
            }
            item {
                TrafficCard(state = state, onRefresh = viewModel::refreshRuntime)
            }
            item {
                ProxyGroupsSection(
                    state = state,
                    onOpenPicker = { nodePickerGroup = it },
                    onTestDelay = viewModel::testDelay,
                )
            }
            item {
                AdvancedDashboardCard(
                    enabled = state.runtimeStatus is RuntimeUiStatus.ProtectedBrowserProxy ||
                        state.runtimeStatus is RuntimeUiStatus.ProtectedStrictVpn,
                    onOpen = onOpenDashboard,
                )
            }
        }

        item {
            DiagnosticsCard(state)
        }
        }

    }
    if (showSubscriptionDialog) {
        SubscriptionDialog(
            onDismiss = { showSubscriptionDialog = false },
            onAdd = { name, url ->
                showSubscriptionDialog = false
                viewModel.addSubscription(name, url)
            },
        )
    }

    pendingDelete?.let { profile ->
        DeleteProfileDialog(
            profile = profile,
            isActive = state.activeProfileId == profile.id,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                viewModel.delete(profile)
            },
        )
    }

    nodePickerGroup?.let { group ->
        NodePickerDialog(
            group = group,
            delays = state.delays,
            onDismiss = { nodePickerGroup = null },
            onSelect = { node ->
                nodePickerGroup = null
                viewModel.selectProxy(group, node)
            },
        )
    }
}

@Composable
private fun SubscriptionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    androidx.compose.material.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.material.Text(
                androidx.compose.ui.res.stringResource(R.string.proxy_add_subscription)
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material.TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        androidx.compose.material.Text(
                            androidx.compose.ui.res.stringResource(R.string.proxy_profile_name)
                        )
                    },
                    singleLine = true,
                )
                androidx.compose.material.TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        androidx.compose.material.Text(
                            androidx.compose.ui.res.stringResource(R.string.proxy_subscription_url)
                        )
                    },
                    singleLine = true,
                )
                androidx.compose.material.Text(
                    androidx.compose.ui.res.stringResource(R.string.proxy_subscription_https_only),
                    style = androidx.compose.material.MaterialTheme.typography.caption,
                )
            }
        },
        confirmButton = {
            androidx.compose.material.TextButton(
                enabled = url.trim().startsWith("https://", ignoreCase = true),
                onClick = { onAdd(name.trim(), url.trim()) },
            ) {
                androidx.compose.material.Text(
                    androidx.compose.ui.res.stringResource(R.string.proxy_add)
                )
            }
        },
        dismissButton = {
            androidx.compose.material.TextButton(onClick = onDismiss) {
                androidx.compose.material.Text(
                    androidx.compose.ui.res.stringResource(android.R.string.cancel)
                )
            }
        },
    )
}

private fun readProfile(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String {
    resolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Unable to open profile" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= 10 * 1024 * 1024) {
                "Profile is larger than 10 MiB"
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
