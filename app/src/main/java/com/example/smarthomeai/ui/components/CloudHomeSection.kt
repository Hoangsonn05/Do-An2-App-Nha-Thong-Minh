package com.example.smarthomeai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.cloud.*
import com.example.smarthomeai.data.nowString
import com.example.smarthomeai.ui.theme.*

@Composable
fun rememberActiveCloudBridge(): CloudUiBridge? = remember { CloudUiBridgeRegistry.currentOrNull() }

@Composable
fun CloudHomeBannerFromBridge(
    bridge: CloudUiBridge,
    singleHomeMode: Boolean = true,
    onOpenManagement: () -> Unit = {}
) {
    val cloudState by bridge.state.collectAsState()
    CloudHomeBannerCard(
        cloudState = cloudState,
        onSelectHome = bridge::selectHome,
        onCreateHome = bridge::createHome,
        onRetry = bridge::retry,
        onDismissFeedback = bridge::dismissFeedback,
        onOpenManagement = onOpenManagement,
        singleHomeMode = singleHomeMode
    )
}

@Composable
fun CloudHomeManagementFromBridge(
    bridge: CloudUiBridge,
    singleHomeMode: Boolean = true
) {
    val cloudState by bridge.state.collectAsState()
    CloudHomeManagementCard(
        cloudState = cloudState,
        onSelectHome = bridge::selectHome,
        onCreateHome = bridge::createHome,
        onLeaveHome = bridge::leaveSelectedHome,
        onRemoveMember = bridge::removeMember,
        onRetry = bridge::retry,
        onDismissFeedback = bridge::dismissFeedback,
        singleHomeMode = singleHomeMode
    )
}

/**
 * Compact Multi-Home Cloud card displayed on HomeScreen.
 * Never blocks, hides, or disables LAN ESP32 controls even when Cloud is unavailable or offline.
 */
@Composable
fun CloudHomeBannerCard(
    cloudState: CloudUiState,
    onSelectHome: (String) -> Unit,
    onCreateHome: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissFeedback: () -> Unit = {},
    onOpenManagement: () -> Unit = {},
    singleHomeMode: Boolean = false
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showSwitchDialog by rememberSaveable { mutableStateOf(false) }

    WhiteCard(Modifier.fillMaxWidth().testTag("cloud-home-banner")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconBadge(
                icon = Icons.Default.CloudQueue,
                tint = statusColor(cloudState.status, cloudState.error),
                bg = statusBackground(cloudState.status, cloudState.error),
                size = 36.dp
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = cloudState.selectedHome?.name ?: "Nhà Cloud",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextDark
                )
                StatusPill(
                    text = statusLabel(cloudState, singleHomeMode),
                    color = statusColor(cloudState.status, cloudState.error),
                    background = statusBackground(cloudState.status, cloudState.error)
                )
            }
        }

        if (cloudState.status == CloudStatus.LOADING || cloudState.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        }

        val helperText = statusDescription(cloudState, singleHomeMode)
        if (helperText != null) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = if (cloudState.status == CloudStatus.ACCESS_REVOKED || cloudState.status == CloudStatus.ERROR) RedAlert else TextGray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (singleHomeMode && cloudState.status == CloudStatus.READY && cloudState.hasMultipleOwnedHomes) {
            CloudWarningBanner(text = legacyMultiHomeWarning(cloudState))
        }

        cloudState.revokedNotice?.takeIf { cloudState.status == CloudStatus.READY }?.let { notice ->
            CloudNoticeBanner(
                text = notice,
                isError = true,
                onDismiss = onDismissFeedback
            )
        }

        cloudState.feedback?.let { fb ->
            CloudNoticeBanner(
                text = fb.message,
                isError = fb.isError,
                onDismiss = onDismissFeedback
            )
        }

        when (cloudState.status) {
            CloudStatus.READY -> {
                if (singleHomeMode) {
                    OutlinedButton(
                        onClick = onOpenManagement,
                        enabled = !cloudState.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Quản lý nhà")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showSwitchDialog = true },
                            enabled = !cloudState.busy,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Default.HomeWork, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nhà (${cloudState.homes.size})")
                        }
                        OutlinedButton(
                            onClick = onOpenManagement,
                            enabled = !cloudState.busy,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Default.Group, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Thành viên")
                        }
                    }
                }
            }
            CloudStatus.EMPTY, CloudStatus.ACCESS_REVOKED -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        enabled = !cloudState.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.AddHome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tạo nhà mới")
                    }
                    if (cloudState.status == CloudStatus.ACCESS_REVOKED) {
                        OutlinedButton(
                            onClick = onRetry,
                            enabled = !cloudState.busy,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Text("Tải lại")
                        }
                    }
                }
            }
            CloudStatus.ERROR -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !cloudState.busy,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Thử lại")
                    }
                }
            }
            CloudStatus.UNAVAILABLE, CloudStatus.SIGNED_OUT, CloudStatus.LOADING -> Unit
        }
    }

    if (showCreateDialog) {
        CreateCloudHomeDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreateHome(name)
            }
        )
    }

    if (!singleHomeMode && showSwitchDialog) {
        HomeSwitcherDialog(
            homes = cloudState.homes,
            selectedHomeId = cloudState.selectedHomeId,
            onSelect = { homeId ->
                showSwitchDialog = false
                onSelectHome(homeId)
            },
            onCreateNew = {
                showSwitchDialog = false
                showCreateDialog = true
            },
            onDismiss = { showSwitchDialog = false }
        )
    }
}

/**
 * Full Multi-Home & Member Management card displayed in SettingsScreen.
 * Enforces role-based actions (OWNER removes MEMBER; MEMBER leaves Home) with confirmation dialogs.
 */
@Composable
fun CloudHomeManagementCard(
    cloudState: CloudUiState,
    onSelectHome: (String) -> Unit,
    onCreateHome: (String) -> Unit,
    onLeaveHome: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissFeedback: () -> Unit = {},
    singleHomeMode: Boolean = false
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveUid by rememberSaveable { mutableStateOf<String?>(null) }

    val canCreateAction = if (singleHomeMode) {
        cloudState.status in setOf(CloudStatus.EMPTY, CloudStatus.ACCESS_REVOKED) && cloudState.ownedHomesCount == 0
    } else {
        cloudState.status in setOf(CloudStatus.READY, CloudStatus.EMPTY, CloudStatus.ACCESS_REVOKED)
    }

    Column(Modifier.fillMaxWidth().testTag("cloud-home-management")) {
        SectionTitle(
            text = if (singleHomeMode) "Nhà Cloud" else "Nhà Cloud & thành viên",
            action = if (canCreateAction) "Tạo nhà" else null,
            onAction = { showCreateDialog = true }
        )
        WhiteCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = statusLabel(cloudState, singleHomeMode),
                    color = statusColor(cloudState.status, cloudState.error),
                    background = statusBackground(cloudState.status, cloudState.error)
                )
                Spacer(Modifier.weight(1f))
                if (cloudState.status != CloudStatus.UNAVAILABLE && cloudState.status != CloudStatus.SIGNED_OUT) {
                    IconButton(
                        onClick = onRetry,
                        enabled = !cloudState.busy
                    ) {
                        Icon(Icons.Default.Refresh, "Tải lại Cloud", tint = Blue600)
                    }
                }
            }

            if (cloudState.status == CloudStatus.LOADING || cloudState.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            val desc = statusDescription(cloudState, singleHomeMode)
            if (desc != null) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cloudState.status == CloudStatus.ACCESS_REVOKED || cloudState.status == CloudStatus.ERROR) RedAlert else TextGray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            cloudState.revokedNotice?.takeIf { cloudState.status == CloudStatus.READY }?.let { notice ->
                CloudNoticeBanner(text = notice, isError = true, onDismiss = onDismissFeedback)
            }

            cloudState.feedback?.let { fb ->
                CloudNoticeBanner(text = fb.message, isError = fb.isError, onDismiss = onDismissFeedback)
            }

            if ((cloudState.status == CloudStatus.EMPTY || cloudState.status == CloudStatus.ACCESS_REVOKED) &&
                (!singleHomeMode || cloudState.ownedHomesCount == 0)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    enabled = !cloudState.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.AddHome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tạo nhà mới")
                }
            }

            if (cloudState.status == CloudStatus.ERROR) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !cloudState.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 44.dp)
                ) {
                    Text("Thử kết nối lại Cloud")
                }
            }

            if (cloudState.status == CloudStatus.READY) {
                if (singleHomeMode) {
                    if (cloudState.hasMultipleOwnedHomes) {
                        CloudWarningBanner(text = legacyMultiHomeWarning(cloudState))
                        Text(
                            text = "Chọn nhà chính trong phiên (${cloudState.ownedHomesCount})",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextDark,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            cloudState.ownedHomes.forEach { home ->
                                HomeChoiceRow(
                                    home = home,
                                    selected = home.id == cloudState.selectedHomeId,
                                    onClick = { onSelectHome(home.id) }
                                )
                            }
                        }
                    } else {
                        val primaryHome = cloudState.selectedHome ?: cloudState.ownedHomes.firstOrNull()
                        if (primaryHome != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Blue50,
                                border = BorderStroke(1.dp, Blue600),
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = Blue600,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(
                                            text = primaryHome.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextDark
                                        )
                                        Text(
                                            text = roleLabel(primaryHome.role),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Blue600
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Danh sách nhà (${cloudState.homes.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextDark,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cloudState.homes.forEach { home ->
                            HomeChoiceRow(
                                home = home,
                                selected = home.id == cloudState.selectedHomeId,
                                onClick = { onSelectHome(home.id) }
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Outline)

                    val selectedHome = cloudState.selectedHome
                    if (selectedHome != null) {
                        Text(
                            text = "Thành viên · ${selectedHome.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextDark
                        )
                        when (cloudState.membersStatus) {
                            MembersStatus.LOADING -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                                Text(
                                    text = "Đang tải danh sách thành viên…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            MembersStatus.ACCESS_REVOKED -> {
                                Text(
                                    text = cloudState.membersErrorMessage ?: "Quyền truy cập nhà này đã bị thu hồi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RedAlert,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            MembersStatus.ERROR -> {
                                Text(
                                    text = cloudState.membersErrorMessage ?: "Không thể tải danh sách thành viên.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RedAlert,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            MembersStatus.IDLE -> Unit
                            MembersStatus.READY -> {
                                if (cloudState.members.isEmpty()) {
                                    Text(
                                        text = "Chưa có thành viên nào.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        cloudState.members.forEach { member ->
                                            MemberRow(
                                                member = member,
                                                isSelf = member.uid == cloudState.currentUid,
                                                canRemove = cloudState.canRemoveMember(member),
                                                onRemoveClick = { confirmRemoveUid = member.uid }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (cloudState.canLeaveSelectedHome) {
                            OutlinedButton(
                                onClick = { confirmLeave = true },
                                enabled = !cloudState.busy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
                                border = BorderStroke(1.dp, RedAlert),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Rời khỏi nhà")
                            }
                        }

                        if (cloudState.isCurrentUserOwner) {
                            Text(
                                text = "Mời thành viên mới (Invitation / Join / QR) thuộc giai đoạn thiết kế hợp đồng riêng.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCloudHomeDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreateHome(name)
            }
        )
    }

    if (confirmLeave) {
        val homeName = cloudState.selectedHome?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Rời khỏi nhà?") },
            text = {
                Text("Bạn sẽ mất quyền truy cập Cloud vào nhà \"$homeName\". Điều khiển ESP32 cục bộ qua LAN không bị ảnh hưởng.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onLeaveHome()
                    }
                ) {
                    Text("Rời khỏi nhà", color = RedAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Hủy") }
            }
        )
    }

    confirmRemoveUid?.let { targetUid ->
        val homeName = cloudState.selectedHome?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmRemoveUid = null },
            title = { Text("Loại bỏ thành viên?") },
            text = {
                Text("Thành viên $targetUid sẽ bị thu hồi quyền truy cập Cloud đối với nhà \"$homeName\".")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveUid = null
                        onRemoveMember(targetUid)
                    }
                ) {
                    Text("Loại bỏ", color = RedAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveUid = null }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun MemberRow(
    member: CloudMember,
    isSelf: Boolean,
    canRemove: Boolean,
    onRemoveClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Bg,
        border = BorderStroke(1.dp, Outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (member.role == CloudRole.OWNER) Icons.Default.VerifiedUser else Icons.Default.Person,
                contentDescription = null,
                tint = if (member.role == CloudRole.OWNER) Blue600 else TextGray,
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    text = if (isSelf) "${member.uid} (Bạn)" else member.uid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextDark
                )
                val metaParts = buildList {
                    add(roleLabel(member.role))
                    member.addedBy?.takeIf { it.isNotBlank() }?.let { add("Thêm bởi: $it") }
                    member.addedAt?.takeIf { it > 0L }?.let { add(nowString(it)) }
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            if (canRemove) {
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.semantics {
                        contentDescription = "Loại bỏ thành viên ${member.uid}"
                    }
                ) {
                    Icon(Icons.Default.PersonRemove, null, tint = RedAlert)
                }
            }
        }
    }
}

@Composable
private fun CloudNoticeBanner(
    text: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isError) RedSoft else GreenSoft,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                contentDescription = null,
                tint = if (isError) RedAlert else GreenOk,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) RedAlert else GreenOk,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Close, "Đóng thông báo", tint = if (isError) RedAlert else GreenOk, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CreateCloudHomeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var rawName by rememberSaveable { mutableStateOf("") }
    val trimmed = rawName.trim()
    val hasForbiddenToken = trimmed.contains("deviceKey", ignoreCase = true)
    val tooLong = trimmed.length > RtdbPayloadBuilder.MAX_HOME_NAME_LENGTH
    val isValid = trimmed.isNotEmpty() && !tooLong && !hasForbiddenToken

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo nhà Cloud mới") },
        text = {
            Column {
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    label = { Text("Tên nhà") },
                    singleLine = true,
                    isError = rawName.isNotEmpty() && !isValid,
                    supportingText = {
                        Text(
                            when {
                                hasForbiddenToken -> "Tên nhà không được chứa từ khóa bảo mật."
                                tooLong -> "Tối đa ${RtdbPayloadBuilder.MAX_HOME_NAME_LENGTH} ký tự."
                                else -> "Ví dụ: Nhà chính, Căn hộ tầng 3"
                            }
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = isValid
            ) { Text("Tạo nhà") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun HomeSwitcherDialog(
    homes: List<CloudHome>,
    selectedHomeId: String?,
    onSelect: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn nhà hoạt động") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                homes.forEach { home ->
                    val selected = home.id == selectedHomeId
                    Surface(
                        onClick = { onSelect(home.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Blue50 else Bg,
                        border = BorderStroke(1.dp, if (selected) Blue600 else Outline),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Home,
                                contentDescription = null,
                                tint = if (selected) Blue600 else TextGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(home.name, style = MaterialTheme.typography.bodyMedium, color = TextDark)
                                Text(roleLabel(home.role), style = MaterialTheme.typography.bodySmall, color = TextGray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) { Text("Tạo nhà mới") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

@Composable
private fun HomeChoiceRow(
    home: CloudHome,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Blue50 else Bg,
        border = BorderStroke(1.dp, if (selected) Blue600 else Outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Blue600 else TextGray,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    text = home.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = TextDark
                )
                Text(
                    text = roleLabel(home.role),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (home.role == CloudRole.OWNER) Blue600 else TextGray
                )
            }
        }
    }
}

@Composable
private fun CloudWarningBanner(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = YellowSoft,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = OrangeWarn,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = OrangeWarn,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }
    }
}

private fun legacyMultiHomeWarning(state: CloudUiState): String =
    "Phát hiện ${state.ownedHomesCount} nhà Cloud trên tài khoản cũ. " +
        "Đang dùng nhà chính \"${state.selectedHome?.name.orEmpty()}\". Hệ thống không tự xóa nhà cũ."

private fun roleLabel(role: CloudRole): String = when (role) {
    CloudRole.OWNER -> "Chủ nhà (OWNER)"
    CloudRole.MEMBER -> "Thành viên (MEMBER)"
}

private fun statusLabel(state: CloudUiState, singleHomeMode: Boolean = false): String = when (state.status) {
    CloudStatus.UNAVAILABLE -> "Cloud chưa khả dụng"
    CloudStatus.SIGNED_OUT -> "Chưa đăng nhập"
    CloudStatus.LOADING -> "Đang tải Cloud…"
    CloudStatus.EMPTY -> "Chưa có nhà"
    CloudStatus.READY -> if (singleHomeMode) {
        "Chủ sở hữu · Đã kết nối Cloud"
    } else {
        state.selectedHome?.let { roleLabel(it.role) } ?: "Đã kết nối Cloud"
    }
    CloudStatus.ACCESS_REVOKED -> "Mất quyền truy cập nhà"
    CloudStatus.ERROR -> when (state.error) {
        CloudError.PermissionDenied -> "Bị từ chối bởi Rules"
        CloudError.Offline, CloudError.Timeout -> "Lỗi kết nối Cloud"
        else -> "Lỗi dữ liệu Cloud"
    }
}

private fun statusDescription(state: CloudUiState, singleHomeMode: Boolean = false): String? = when (state.status) {
    CloudStatus.UNAVAILABLE -> "Ứng dụng chưa có cấu hình Cloud hợp lệ. Điều khiển ESP32 qua LAN vẫn hoạt động bình thường."
    CloudStatus.SIGNED_OUT -> "Đăng nhập bằng tài khoản Firebase để đồng bộ danh sách nhà và thành viên."
    CloudStatus.LOADING -> "Đang tải danh sách nhà cho tài khoản hiện tại…"
    CloudStatus.EMPTY -> if (singleHomeMode && state.hasOnlyMemberHomes) {
        "Tài khoản đang chỉ tham gia nhà được chia sẻ; chế độ chia sẻ nhiều thành viên hiện tạm hoãn. Bạn có thể tạo nhà riêng cho ESP32 của mình."
    } else {
        "Tài khoản hiện chưa thuộc nhà Cloud nào. Bạn có thể tạo nhà mới."
    }
    CloudStatus.ACCESS_REVOKED -> state.revokedNotice ?: "Quyền truy cập vào nhà trước đó đã bị thu hồi hoặc nhà không còn tồn tại."
    CloudStatus.ERROR -> state.errorMessage ?: "Không thể kết nối Cloud. Điều khiển ESP32 qua LAN vẫn hoạt động bình thường."
    CloudStatus.READY -> null
}

private fun statusColor(status: CloudStatus, error: CloudError?) = when (status) {
    CloudStatus.READY -> Blue600
    CloudStatus.LOADING, CloudStatus.EMPTY, CloudStatus.SIGNED_OUT -> TextGray
    CloudStatus.UNAVAILABLE -> OrangeWarn
    CloudStatus.ACCESS_REVOKED -> RedAlert
    CloudStatus.ERROR -> if (error == CloudError.PermissionDenied) RedAlert else OrangeWarn
}

private fun statusBackground(status: CloudStatus, error: CloudError?) = when (status) {
    CloudStatus.READY -> Blue50
    CloudStatus.LOADING, CloudStatus.EMPTY, CloudStatus.SIGNED_OUT -> OffGray
    CloudStatus.UNAVAILABLE -> YellowSoft
    CloudStatus.ACCESS_REVOKED -> RedSoft
    CloudStatus.ERROR -> if (error == CloudError.PermissionDenied) RedSoft else YellowSoft
}
