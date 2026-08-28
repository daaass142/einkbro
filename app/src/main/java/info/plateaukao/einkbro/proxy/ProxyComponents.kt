package info.plateaukao.einkbro.proxy

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.BuildConfig
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.core.mihomo.api.ProxyGroup
import info.plateaukao.einkbro.core.mihomo.api.RoutingMode
import info.plateaukao.einkbro.core.mihomo.profile.ProfileRecord
import info.plateaukao.einkbro.core.mihomo.profile.ProfileSourceType
import info.plateaukao.einkbro.preference.ProxyTransportMode
import java.net.URI

@Composable
internal fun RuntimeStatusCard(
    state: ProxyUiState,
    onRetry: () -> Unit,
    onDirectOnce: () -> Unit,
) {
    val statusTitle = when (state.runtimeStatus) {
        RuntimeUiStatus.Off -> stringResource(R.string.proxy_status_off)
        RuntimeUiStatus.Starting -> stringResource(R.string.proxy_status_starting)
        is RuntimeUiStatus.ProtectedBrowserProxy ->
            stringResource(R.string.proxy_status_protected_browser)
        is RuntimeUiStatus.ProtectedStrictVpn ->
            stringResource(R.string.proxy_status_protected_vpn)
        is RuntimeUiStatus.Blocked -> stringResource(R.string.proxy_status_blocked)
        RuntimeUiStatus.TemporaryDirect -> stringResource(R.string.proxy_status_temporary_direct)
        is RuntimeUiStatus.Error -> stringResource(R.string.proxy_status_error)
    }

    ProxyPanel(borderWidth = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.proxy_status_title), style = MaterialTheme.typography.caption)
            Text(statusTitle, style = MaterialTheme.typography.h6)

            when (val runtime = state.runtimeStatus) {
                RuntimeUiStatus.Off -> Text(stringResource(R.string.proxy_status_off_summary))
                RuntimeUiStatus.Starting -> Text(stringResource(R.string.proxy_status_starting_summary))
                is RuntimeUiStatus.ProtectedBrowserProxy ->
                    Text(stringResource(R.string.proxy_status_browser_summary))
                is RuntimeUiStatus.ProtectedStrictVpn ->
                    Text(stringResource(R.string.proxy_status_vpn_summary))
                is RuntimeUiStatus.Blocked ->
                    Text(runtime.reason ?: stringResource(R.string.proxy_status_blocked_summary))
                RuntimeUiStatus.TemporaryDirect ->
                    Text(stringResource(R.string.proxy_status_temporary_direct_summary))
                is RuntimeUiStatus.Error ->
                    Text(runtime.reason ?: stringResource(R.string.proxy_status_error_summary))
            }

            state.activeProfile?.let { profile ->
                Divider()
                Text(
                    stringResource(
                        R.string.proxy_status_profile_mode,
                        profile.name,
                        routingModeLabel(state.routingMode),
                    ),
                    style = MaterialTheme.typography.body2,
                )
                state.primarySelectedProxy?.let { node ->
                    Text(
                        stringResource(R.string.proxy_status_node, node),
                        style = MaterialTheme.typography.body2,
                    )
                }
            }

            state.currentAction?.let {
                Text(actionLabel(it), style = MaterialTheme.typography.caption)
            }

            if (
                state.runtimeStatus is RuntimeUiStatus.Blocked ||
                state.runtimeStatus is RuntimeUiStatus.Error
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry, enabled = state.currentAction == null) {
                        Text(stringResource(R.string.proxy_retry))
                    }
                    OutlinedButton(onClick = onDirectOnce, enabled = state.currentAction == null) {
                        Text(stringResource(R.string.proxy_use_direct_once))
                    }
                }
            } else if (state.runtimeStatus == RuntimeUiStatus.TemporaryDirect) {
                Button(onClick = onRetry, enabled = state.currentAction == null) {
                    Text(stringResource(R.string.proxy_retry_mihomo))
                }
            }
        }
    }
}

@Composable
internal fun EnableProxyCard(
    state: ProxyUiState,
    onEnabledChange: (Boolean) -> Unit,
) {
    SettingsSwitchCard(
        title = stringResource(R.string.proxy_enable),
        summary = stringResource(R.string.proxy_enable_summary),
        checked = state.enabled,
        enabled = state.currentAction == null,
        onCheckedChange = onEnabledChange,
    )
}

@Composable
internal fun TransportCard(
    state: ProxyUiState,
    onSelectBrowser: () -> Unit,
    onSelectStrictVpn: () -> Unit,
) {
    ProxyPanel() {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.proxy_transport),
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            TransportRow(
                selected = state.transportMode == ProxyTransportMode.BROWSER_PROXY,
                title = stringResource(R.string.proxy_transport_browser),
                summary = stringResource(R.string.proxy_transport_browser_summary),
                enabled = state.currentAction == null,
                onClick = onSelectBrowser,
            )
            TransportRow(
                selected = state.transportMode == ProxyTransportMode.STRICT_VPN,
                title = stringResource(R.string.proxy_transport_strict),
                summary = stringResource(R.string.proxy_transport_strict_summary),
                enabled = state.currentAction == null,
                onClick = onSelectStrictVpn,
            )
        }
    }
}

@Composable
private fun TransportRow(
    selected: Boolean,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.body1)
            Text(summary, style = MaterialTheme.typography.body2)
        }
    }
}

@Composable
internal fun FailClosedCard(
    state: ProxyUiState,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchCard(
        title = stringResource(R.string.proxy_fail_closed),
        summary = stringResource(R.string.proxy_fail_closed_summary),
        checked = state.failClosed,
        enabled = state.currentAction == null,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ProxyPanel() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.subtitle1)
                Text(summary, style = MaterialTheme.typography.body2)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
internal fun ProxyErrorCard(
    error: ProxyUiError,
    onDismiss: () -> Unit,
) {
    ProxyPanel() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(errorTitle(error.category), style = MaterialTheme.typography.subtitle1)
            Text(
                if (error.message.isBlank()) errorDefaultMessage(error.category) else error.message,
                style = MaterialTheme.typography.body2,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

@Composable
internal fun ProfileSection(
    state: ProxyUiState,
    onImport: () -> Unit,
    onAddSubscription: () -> Unit,
    onSelect: (ProfileRecord) -> Unit,
    onRefresh: (ProfileRecord) -> Unit,
    onDeleteRequest: (ProfileRecord) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.proxy_profiles))

        if (state.profiles.isEmpty()) {
            ProxyPanel() {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.proxy_no_profiles_title),
                        style = MaterialTheme.typography.subtitle1,
                    )
                    Text(stringResource(R.string.proxy_no_profiles))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onImport) {
                            Text(stringResource(R.string.proxy_import_yaml))
                        }
                        OutlinedButton(onClick = onAddSubscription) {
                            Text(stringResource(R.string.proxy_add_subscription))
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onImport, enabled = state.currentAction == null) {
                    Text(stringResource(R.string.proxy_import_yaml))
                }
                OutlinedButton(
                    onClick = onAddSubscription,
                    enabled = state.currentAction == null,
                ) {
                    Text(stringResource(R.string.proxy_add_subscription))
                }
            }

            state.profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    selected = state.activeProfileId == profile.id,
                    currentAction = state.currentAction,
                    onSelect = { onSelect(profile) },
                    onRefresh = { onRefresh(profile) },
                    onDeleteRequest = { onDeleteRequest(profile) },
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileRecord,
    selected: Boolean,
    currentAction: ProxyAction?,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val busy = when (currentAction) {
        is ProxyAction.ActivatingProfile -> currentAction.profileId == profile.id
        is ProxyAction.RefreshingSubscription -> currentAction.profileId == profile.id
        is ProxyAction.DeletingProfile -> currentAction.profileId == profile.id
        else -> false
    }

    ProxyPanel() {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = currentAction == null, role = Role.RadioButton, onClick = onSelect),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    enabled = currentAction == null,
                    onClick = null,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.subtitle1)
                    Text(profileSourceLabel(profile), style = MaterialTheme.typography.body2)
                    if (profile.updatedAt > 0L) {
                        Text(
                            stringResource(
                                R.string.proxy_updated_at,
                                relativeUpdatedAt(profile.updatedAt),
                            ),
                            style = MaterialTheme.typography.caption,
                        )
                    }
                    profile.lastError?.let {
                        Text(it, style = MaterialTheme.typography.caption)
                    }
                    if (busy) {
                        Text(actionLabel(currentAction!!), style = MaterialTheme.typography.caption)
                    }
                }
            }
            Row {
                if (profile.sourceType == ProfileSourceType.SUBSCRIPTION) {
                    TextButton(enabled = currentAction == null, onClick = onRefresh) {
                        Text(stringResource(R.string.proxy_refresh_subscription))
                    }
                }
                TextButton(enabled = currentAction == null, onClick = onDeleteRequest) {
                    Text(stringResource(R.string.proxy_delete))
                }
            }
        }
    }
}

@Composable
internal fun RoutingModeCard(
    state: ProxyUiState,
    onMode: (RoutingMode) -> Unit,
) {
    ProxyPanel() {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.proxy_routing_mode), style = MaterialTheme.typography.subtitle1)
            RoutingMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state.currentAction == null, role = Role.RadioButton) { onMode(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.routingMode == mode,
                        enabled = state.currentAction == null,
                        onClick = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(routingModeLabel(mode))
                        if (mode == RoutingMode.DIRECT) {
                            Text(
                                stringResource(R.string.proxy_direct_via_mihomo_summary),
                                style = MaterialTheme.typography.caption,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrafficCard(
    state: ProxyUiState,
    onRefresh: () -> Unit,
) {
    ProxyPanel() {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.proxy_traffic_title), style = MaterialTheme.typography.subtitle1)
                Text(
                    stringResource(
                        R.string.proxy_traffic,
                        formatBytes(state.traffic.downloadBytes),
                        formatBytes(state.traffic.uploadBytes),
                    )
                )
            }
            TextButton(enabled = state.currentAction == null, onClick = onRefresh) {
                Text(stringResource(R.string.proxy_refresh))
            }
        }
    }
}

@Composable
internal fun ProxyGroupsSection(
    state: ProxyUiState,
    onOpenPicker: (ProxyGroup) -> Unit,
    onTestDelay: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.proxy_groups))

        if (state.groups.isEmpty()) {
            Text(stringResource(R.string.proxy_no_groups), style = MaterialTheme.typography.body2)
        } else {
            state.groups.forEach { group ->
                ProxyGroupCard(
                    group = group,
                    delays = state.delays,
                    currentAction = state.currentAction,
                    onOpenPicker = { onOpenPicker(group) },
                    onTestDelay = onTestDelay,
                )
            }
        }
    }
}

@Composable
private fun ProxyGroupCard(
    group: ProxyGroup,
    delays: Map<String, Int>,
    currentAction: ProxyAction?,
    onOpenPicker: () -> Unit,
    onTestDelay: (String) -> Unit,
) {
    val selected = group.selected
    val groupBusy = currentAction is ProxyAction.SwitchingNode &&
        currentAction.groupName == group.name
    val delayBusy = selected != null &&
        currentAction is ProxyAction.TestingDelay &&
        currentAction.proxyName == selected

    ProxyPanel() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.name, style = MaterialTheme.typography.subtitle1)
            Text(
                selected ?: stringResource(R.string.proxy_no_selected_node),
                style = MaterialTheme.typography.body1,
            )
            selected?.let { node ->
                knownDelayText(delays[node])?.let { delay ->
                    Text(delay, style = MaterialTheme.typography.caption)
                }
            }
            if (groupBusy || delayBusy) {
                Text(actionLabel(currentAction!!), style = MaterialTheme.typography.caption)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = currentAction == null && group.proxies.isNotEmpty(),
                    onClick = onOpenPicker,
                ) {
                    Text(stringResource(R.string.proxy_change_node))
                }
                selected?.let { node ->
                    TextButton(
                        enabled = currentAction == null,
                        onClick = { onTestDelay(node) },
                    ) {
                        Text(
                            knownDelayText(delays[node])
                                ?: stringResource(R.string.proxy_test_delay)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NodePickerDialog(
    group: ProxyGroup,
    delays: Map<String, Int>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember(group.name) { mutableStateOf("") }
    val filtered = remember(group.proxies, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) group.proxies
        else group.proxies.filter { it.contains(normalized, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(group.name)
                Text(
                    stringResource(R.string.proxy_choose_node),
                    style = MaterialTheme.typography.caption,
                )
            }
        },
        text = {
            Column {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.proxy_search_nodes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered) { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { onSelect(node) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = group.selected == node, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(node, modifier = Modifier.weight(1f))
                            knownDelayText(delays[node])?.let {
                                Text(it, style = MaterialTheme.typography.caption)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun AdvancedDashboardCard(
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    ProxyPanel() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.proxy_advanced_title), style = MaterialTheme.typography.subtitle1)
            Text(stringResource(R.string.proxy_advanced_summary), style = MaterialTheme.typography.body2)
            Button(enabled = enabled, onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.proxy_open_dashboard))
            }
        }
    }
}

@Composable
internal fun DiagnosticsCard(state: ProxyUiState) {
    var expanded by remember { mutableStateOf(false) }

    ProxyPanel(
        modifier = Modifier.clickable(
            role = Role.Button,
            onClick = { expanded = !expanded },
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.proxy_diagnostics), style = MaterialTheme.typography.subtitle1)
            Text(
                if (expanded) stringResource(R.string.proxy_diagnostics_hide)
                else stringResource(R.string.proxy_diagnostics_show),
                style = MaterialTheme.typography.caption,
            )

            if (expanded) {
                Divider(Modifier.padding(vertical = 6.dp))
                DiagnosticRow(stringResource(R.string.proxy_diag_transport), transportLabel(state.transportMode))
                DiagnosticRow(stringResource(R.string.proxy_diag_runtime), runtimeDiagnosticLabel(state.runtimeStatus))
                DiagnosticRow(stringResource(R.string.proxy_diag_profile), state.activeProfile?.name ?: "—")
                DiagnosticRow("libmihomo", BuildConfig.LIBMIHOMO_VERSION)
                DiagnosticRow("mihomo", BuildConfig.MIHOMO_CORE_VERSION)
                DiagnosticRow("bridgeABI", BuildConfig.MIHOMO_BRIDGE_ABI.toString())
                DiagnosticRow("Zashboard", BuildConfig.ZASHBOARD_VERSION)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.body2)
        Text(value, style = MaterialTheme.typography.body2)
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.h6,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun DeleteProfileDialog(
    profile: ProfileRecord,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proxy_delete_profile_title, profile.name)) },
        text = {
            Text(
                if (isActive) stringResource(R.string.proxy_delete_active_profile_message)
                else stringResource(R.string.proxy_delete_profile_message)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.proxy_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun profileSourceLabel(profile: ProfileRecord): String =
    when (profile.sourceType) {
        ProfileSourceType.LOCAL -> stringResource(R.string.proxy_local_profile)
        ProfileSourceType.SUBSCRIPTION -> {
            val host = runCatching { URI(profile.sourceUrl.orEmpty()).host }
                .getOrNull()
                .orEmpty()
            val safeHost = if (host.isBlank()) {
                stringResource(R.string.proxy_subscription)
            } else {
                host
            }
            stringResource(R.string.proxy_subscription_host, safeHost)
        }
    }

@Composable
private fun errorTitle(category: ProxyErrorCategory): String =
    when (category) {
        ProxyErrorCategory.PROFILE_REQUIRED -> stringResource(R.string.proxy_error_profile_required)
        ProxyErrorCategory.PROFILE -> stringResource(R.string.proxy_error_profile)
        ProxyErrorCategory.SUBSCRIPTION -> stringResource(R.string.proxy_error_subscription)
        ProxyErrorCategory.APP_INCOMPATIBLE -> stringResource(R.string.proxy_error_app)
        ProxyErrorCategory.VPN_PERMISSION -> stringResource(R.string.proxy_error_vpn_permission)
        ProxyErrorCategory.RUNTIME -> stringResource(R.string.proxy_error_runtime)
        ProxyErrorCategory.UNKNOWN -> stringResource(R.string.proxy_error)
    }

@Composable
private fun errorDefaultMessage(category: ProxyErrorCategory): String =
    when (category) {
        ProxyErrorCategory.VPN_PERMISSION ->
            stringResource(R.string.proxy_vpn_permission_denied_message)
        ProxyErrorCategory.PROFILE_REQUIRED ->
            stringResource(R.string.proxy_profile_required_message)
        else -> stringResource(R.string.proxy_status_error_summary)
    }

@Composable
private fun actionLabel(action: ProxyAction): String =
    when (action) {
        ProxyAction.StartingRuntime -> stringResource(R.string.proxy_action_starting)
        ProxyAction.StoppingRuntime -> stringResource(R.string.proxy_action_stopping)
        ProxyAction.SwitchingTransport -> stringResource(R.string.proxy_action_switching_transport)
        ProxyAction.ImportingProfile -> stringResource(R.string.proxy_action_importing)
        ProxyAction.AddingSubscription -> stringResource(R.string.proxy_action_adding_subscription)
        is ProxyAction.ActivatingProfile -> stringResource(R.string.proxy_action_switching_profile)
        is ProxyAction.RefreshingSubscription -> stringResource(R.string.proxy_action_refreshing_subscription)
        is ProxyAction.DeletingProfile -> stringResource(R.string.proxy_action_deleting_profile)
        ProxyAction.ChangingRouting -> stringResource(R.string.proxy_action_changing_routing)
        is ProxyAction.SwitchingNode -> stringResource(R.string.proxy_action_switching_node)
        is ProxyAction.TestingDelay -> stringResource(R.string.proxy_action_testing_delay)
        ProxyAction.RefreshingRuntime -> stringResource(R.string.proxy_action_refreshing)
        ProxyAction.RetryingRuntime -> stringResource(R.string.proxy_action_retrying)
        ProxyAction.EnteringTemporaryDirect -> stringResource(R.string.proxy_action_direct_once)
    }

@Composable
internal fun routingModeLabel(mode: RoutingMode): String =
    when (mode) {
        RoutingMode.RULE -> stringResource(R.string.proxy_mode_rule)
        RoutingMode.GLOBAL -> stringResource(R.string.proxy_mode_global)
        RoutingMode.DIRECT -> stringResource(R.string.proxy_mode_direct_mihomo)
    }

@Composable
private fun transportLabel(mode: ProxyTransportMode): String =
    when (mode) {
        ProxyTransportMode.BROWSER_PROXY -> stringResource(R.string.proxy_transport_browser)
        ProxyTransportMode.STRICT_VPN -> stringResource(R.string.proxy_transport_strict)
    }

@Composable
private fun runtimeDiagnosticLabel(status: RuntimeUiStatus): String =
    when (status) {
        RuntimeUiStatus.Off -> stringResource(R.string.proxy_status_off)
        RuntimeUiStatus.Starting -> stringResource(R.string.proxy_status_starting)
        is RuntimeUiStatus.ProtectedBrowserProxy -> stringResource(R.string.proxy_status_protected_browser)
        is RuntimeUiStatus.ProtectedStrictVpn -> stringResource(R.string.proxy_status_protected_vpn)
        is RuntimeUiStatus.Blocked -> stringResource(R.string.proxy_status_blocked)
        RuntimeUiStatus.TemporaryDirect -> stringResource(R.string.proxy_status_temporary_direct)
        is RuntimeUiStatus.Error -> stringResource(R.string.proxy_status_error)
    }

@Composable
private fun knownDelayText(delay: Int?): String? =
    when {
        delay == null -> null
        delay < 0 -> stringResource(R.string.proxy_delay_failed)
        else -> "$delay ms"
    }

private fun relativeUpdatedAt(updatedAt: Long): String =
    DateUtils.getRelativeTimeSpanString(
        updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0)
    return when {
        safe >= 1024L * 1024L * 1024L -> "%.1f GiB".format(safe / (1024.0 * 1024.0 * 1024.0))
        safe >= 1024L * 1024L -> "%.1f MiB".format(safe / (1024.0 * 1024.0))
        safe >= 1024L -> "%.1f KiB".format(safe / 1024.0)
        else -> "$safe B"
    }
}


@Composable
private fun ProxyPanel(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(borderWidth, MaterialTheme.colors.onBackground),
        elevation = 0.dp,
        content = content,
    )
}
