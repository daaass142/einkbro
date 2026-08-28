package info.plateaukao.einkbro.proxy

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.core.mihomo.api.ProxyGroup
import info.plateaukao.einkbro.core.mihomo.api.RoutingMode
import info.plateaukao.einkbro.core.mihomo.profile.ProfileRecord
import info.plateaukao.einkbro.core.mihomo.profile.ProfileSourceType
import java.io.ByteArrayOutputStream
import java.net.URI
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

    val profilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val yaml = withContext(Dispatchers.IO) {
                    readProfile(context.contentResolver, uri)
                }
                val name = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.')
                    .orEmpty()
                    .ifBlank { context.getString(R.string.proxy_local_profile) }
                viewModel.importLocal(name, yaml)
            }
        }
    }

    LaunchedEffect(state.enabled, state.activeProfileId) {
        if (state.enabled && state.activeProfileId.isNotBlank()) {
            viewModel.refreshRuntime()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingToggleCard(
                title = stringResource(R.string.proxy_enable),
                summary = stringResource(R.string.proxy_enable_summary),
                checked = state.enabled,
                onChecked = viewModel::setEnabled,
            )
        }
        item {
            SettingToggleCard(
                title = stringResource(R.string.proxy_fail_closed),
                summary = stringResource(R.string.proxy_fail_closed_summary),
                checked = state.failClosed,
                onChecked = viewModel::setFailClosed,
            )
        }

        if (state.error != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.proxy_error),
                            style = MaterialTheme.typography.subtitle1,
                        )
                        Text(state.error.orEmpty())
                        TextButton(onClick = viewModel::clearError) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        profilePicker.launch(arrayOf("text/*", "application/yaml", "application/octet-stream"))
                    }
                ) {
                    Text(stringResource(R.string.proxy_import_yaml))
                }
                Button(onClick = { showSubscriptionDialog = true }) {
                    Text(stringResource(R.string.proxy_add_subscription))
                }
            }
        }

        item {
            Text(
                stringResource(R.string.proxy_profiles),
                style = MaterialTheme.typography.h6,
            )
        }

        if (state.profiles.isEmpty()) {
            item { Text(stringResource(R.string.proxy_no_profiles)) }
        } else {
            items(state.profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    selected = state.activeProfileId == profile.id,
                    onSelect = { viewModel.activate(profile) },
                    onRefresh = { viewModel.refreshSubscription(profile) },
                    onDelete = { viewModel.delete(profile) },
                )
            }
        }

        if (state.enabled && state.activeProfileId.isNotBlank()) {
            item {
                Text(
                    stringResource(R.string.proxy_routing_mode),
                    style = MaterialTheme.typography.h6,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RoutingMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.routingMode == mode,
                                onClick = { viewModel.setRoutingMode(mode) },
                            )
                            Text(mode.name)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.proxy_traffic,
                            formatBytes(state.traffic.downloadBytes),
                            formatBytes(state.traffic.uploadBytes),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::refreshRuntime) {
                        Text(stringResource(R.string.proxy_refresh))
                    }
                }
            }

            items(state.groups, key = { it.name }) { group ->
                ProxyGroupCard(
                    group = group,
                    delays = state.delays,
                    onSelect = { viewModel.selectProxy(group, it) },
                    onDelay = viewModel::testDelay,
                )
            }

            item {
                Button(
                    onClick = onOpenDashboard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.proxy_open_dashboard))
                }
            }
        }

        if (state.busy) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
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
}

@Composable
private fun SettingToggleCard(
    title: String,
    summary: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.subtitle1)
                Text(summary, style = MaterialTheme.typography.body2)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileRecord,
    selected: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.subtitle1)
                    Text(
                        when (profile.sourceType) {
                            ProfileSourceType.LOCAL -> stringResource(R.string.proxy_local_profile)
                            ProfileSourceType.SUBSCRIPTION ->
                                safeSubscriptionHost(profile.sourceUrl)
                        },
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
            profile.lastError?.let {
                Text(it, style = MaterialTheme.typography.caption)
            }
            Row {
                if (profile.sourceType == ProfileSourceType.SUBSCRIPTION) {
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.proxy_refresh_subscription))
                    }
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun ProxyGroupCard(
    group: ProxyGroup,
    delays: Map<String, Int>,
    onSelect: (String) -> Unit,
    onDelay: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(group.name, style = MaterialTheme.typography.subtitle1)
            Text(group.selected ?: "-", style = MaterialTheme.typography.body2)
            Row {
                TextButton(onClick = { expanded = true }) {
                    Text(stringResource(R.string.proxy_change_node))
                }
                group.selected?.let { selected ->
                    TextButton(onClick = { onDelay(selected) }) {
                        val delay = delays[selected]
                        Text(
                            if (delay == null) {
                                stringResource(R.string.proxy_test_delay)
                            } else if (delay < 0) {
                                stringResource(R.string.proxy_delay_failed)
                            } else {
                                "${delay} ms"
                            }
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    group.proxies.forEach { name ->
                        DropdownMenuItem(
                            onClick = {
                                expanded = false
                                onSelect(name)
                            }
                        ) {
                            Text(name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proxy_add_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.proxy_profile_name)) },
                    singleLine = true,
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.proxy_subscription_url)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.startsWith("https://"),
                onClick = { onAdd(name, url) },
            ) {
                Text(stringResource(R.string.proxy_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun safeSubscriptionHost(value: String?): String {
    if (value.isNullOrBlank()) return "Subscription"
    return runCatching { URI(value).host }.getOrNull().orEmpty().ifBlank { "Subscription" }
}

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0)
    return when {
        safe >= 1024L * 1024L * 1024L -> "%.1f GiB".format(safe / (1024.0 * 1024.0 * 1024.0))
        safe >= 1024L * 1024L -> "%.1f MiB".format(safe / (1024.0 * 1024.0))
        safe >= 1024L -> "%.1f KiB".format(safe / 1024.0)
        else -> "$safe B"
    }
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
            require(total <= 10 * 1024 * 1024) { "Profile is larger than 10 MiB" }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
