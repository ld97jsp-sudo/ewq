package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.FirebaseChannel
import com.example.models.FirebaseCategory
import com.example.viewmodel.XtreamViewModel

@Composable
fun DeveloperDashboard(
    viewModel: XtreamViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(1) } // 1: Publish Channel, 2: Publish Category, 3: Manage Content
    val fbCategories by viewModel.fbCategories.collectAsState()
    val fbChannels by viewModel.fbChannels.collectAsState()

    val primaryColor = when (viewModel.currentTheme.collectAsState().value) {
        "netflix" -> Color(0xFFE50914)
        "midnight" -> Color(0xFF00ADB5)
        "sunset" -> Color(0xFFBB86FC)
        "emerald" -> Color(0xFF03C988)
        else -> Color(0xFFFF9800)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D14))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.translate("admin_title"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close admin", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub Navigation Tabs Inside Admin Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    1 to viewModel.translate("admin_pub_chan"),
                    2 to viewModel.translate("admin_pub_cat"),
                    3 to viewModel.translate("admin_manage"),
                    4 to "إعدادات اليوتيوب"
                ).forEach { (idx, label) ->
                    Button(
                        onClick = { activeSubTab = idx },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeSubTab == idx) primaryColor else Color(0xFF1E1E2E)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label, 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (activeSubTab) {
                    1 -> PublishChannelForm(viewModel, fbCategories, primaryColor)
                    2 -> PublishCategoryForm(viewModel, primaryColor)
                    3 -> ManageContentLists(viewModel, fbChannels, fbCategories, primaryColor)
                    4 -> YoutubeAdminPanel(viewModel, primaryColor)
                }
            }
        }
    }
}

@Composable
fun PublishChannelForm(
    viewModel: XtreamViewModel,
    categories: List<FirebaseCategory>,
    primaryColor: Color
) {
    var name by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var streamType by remember { mutableStateOf("Smart View") }
    var userAgent by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }

    var expandedType by remember { mutableStateOf(false) }
    var expandedCat by remember { mutableStateOf(false) }

    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isPosting by remember { mutableStateOf(false) }

    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedCategoryId.isEmpty()) {
            selectedCategoryId = categories.first().id
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(viewModel.translate("admin_chan_name"), color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = primaryColor
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = logoUrl,
                onValueChange = { logoUrl = it },
                label = { Text(viewModel.translate("admin_chan_logo"), color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = primaryColor
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = streamUrl,
                onValueChange = { streamUrl = it },
                label = { Text(viewModel.translate("admin_chan_url"), color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = primaryColor
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = userAgent,
                onValueChange = { userAgent = it },
                label = { Text(viewModel.translate("admin_chan_ua"), color = Color.Gray) },
                placeholder = { Text("Mozilla/5.0... (اختياري)", color = Color.DarkGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = primaryColor
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Stream Type Dropdown Selector
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { expandedType = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2E)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("${viewModel.translate("admin_chan_engine")}: $streamType", color = Color.White)
                }

                DropdownMenu(
                    expanded = expandedType,
                    onDismissRequest = { expandedType = false },
                    modifier = Modifier.width(300.dp).background(Color(0xFF1E1E2E))
                ) {
                    val list = listOf("Smart View", "Proxy", "TS", "M3U8", "Regular")
                    list.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item, color = Color.White) },
                            onClick = {
                                streamType = item
                                expandedType = false
                            }
                        )
                    }
                }
            }
        }

        // Category Selector Dropdown
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                val catName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "الرئيسية (عام)"
                Button(
                    onClick = { expandedCat = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2E)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("${viewModel.translate("admin_chan_cat")}: $catName", color = Color.White)
                }

                DropdownMenu(
                    expanded = expandedCat,
                    onDismissRequest = { expandedCat = false },
                    modifier = Modifier.width(300.dp).background(Color(0xFF1E1E2E))
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name, color = Color.White) },
                            onClick = {
                                selectedCategoryId = cat.id
                                expandedCat = false
                            }
                        )
                    }
                }
            }
        }

        item {
            // Status messages
            statusMsg?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.contains("نجاح") || msg.contains("successfully")) Color.Green else Color.Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
        }

        item {
            if (isPosting) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else {
                Button(
                    onClick = {
                        if (name.isBlank() || streamUrl.isBlank()) {
                            statusMsg = viewModel.translate("status_error")
                            return@Button
                        }
                        isPosting = true
                        statusMsg = null
                        val newChan = FirebaseChannel(
                            name = name,
                            logoUrl = logoUrl,
                            streamUrl = streamUrl,
                            streamType = streamType,
                            userAgent = userAgent,
                            categoryId = if (selectedCategoryId.isEmpty()) "all" else selectedCategoryId
                        )
                        viewModel.publishFbChannel(newChan) { ok ->
                            isPosting = false
                            if (ok) {
                                statusMsg = viewModel.translate("status_published")
                                name = ""
                                logoUrl = ""
                                streamUrl = ""
                                userAgent = ""
                            } else {
                                statusMsg = viewModel.translate("status_error")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_pub_chan_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Publish, contentDescription = null)
                        Text(viewModel.translate("pub_button"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PublishCategoryForm(
    viewModel: XtreamViewModel,
    primaryColor: Color
) {
    var name by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(viewModel.translate("admin_cat_name"), color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = primaryColor
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text(viewModel.translate("admin_cat_url"), color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = primaryColor
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            statusMsg?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.contains("نجاح") || msg.contains("successfully")) Color.Green else Color.Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            if (isPosting) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            statusMsg = viewModel.translate("status_error")
                            return@Button
                        }
                        isPosting = true
                        statusMsg = null
                        viewModel.publishFbCategory(name, imageUrl) { ok ->
                            isPosting = false
                            if (ok) {
                                statusMsg = viewModel.translate("status_published")
                                name = ""
                                imageUrl = ""
                            } else {
                                statusMsg = viewModel.translate("status_error")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_pub_cat_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Publish, contentDescription = null)
                        Text(viewModel.translate("pub_button"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ManageContentLists(
    viewModel: XtreamViewModel,
    channels: List<FirebaseChannel>,
    categories: List<FirebaseCategory>,
    primaryColor: Color
) {
    var isOperating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Categories list
        item {
            Text(
                text = "📁 ${viewModel.translate("categories_header")} (${categories.size})",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (categories.isEmpty()) {
            item {
                Text("لا يوجد أقسام منشورة أونلاين بعد.", color = Color.Gray, fontSize = 11.sp)
            }
        } else {
            items(categories) { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF151622))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(cat.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("ID: ${cat.id}", color = Color.Gray, fontSize = 9.sp)
                    }

                    IconButton(
                        onClick = {
                            isOperating = true
                            viewModel.deleteFbCategory(cat.id) { _ ->
                                isOperating = false
                            }
                        },
                        enabled = !isOperating
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete category", tint = Color.Red)
                    }
                }
            }
        }

        // Channels listed
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "📺 القنوات النشطة (${channels.size})",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (channels.isEmpty()) {
            item {
                Text("لا يوجد قنوات منشورة أونلاين بعد.", color = Color.Gray, fontSize = 11.sp)
            }
        } else {
            items(channels) { chan ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF151622))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chan.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Engine: ${chan.streamType} | Category: ${chan.categoryId}", color = Color.LightGray, fontSize = 10.sp)
                        Text(chan.streamUrl, color = primaryColor, fontSize = 9.sp, maxLines = 1)
                        if (chan.userAgent.isNotEmpty()) {
                            Text("User-Agent: ${chan.userAgent}", color = Color.Gray, fontSize = 8.sp, maxLines = 1)
                        }
                    }

                    IconButton(
                        onClick = {
                            isOperating = true
                            viewModel.deleteFbChannel(chan.id) { _ ->
                                isOperating = false
                            }
                        },
                        enabled = !isOperating
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete channel", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun YoutubeAdminPanel(
    viewModel: XtreamViewModel,
    primaryColor: Color
) {
    val enabledLocal by viewModel.isYoutubeEnabled.collectAsState()
    val apiKeyLocal by viewModel.youtubeApiKey.collectAsState()

    var enabledInput by remember { mutableStateOf(enabledLocal) }
    var apiKeyInput by remember { mutableStateOf(apiKeyLocal) }
    
    var isSaving by remember { mutableStateOf(false) }
    var saveStatusMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(enabledLocal, apiKeyLocal) {
        enabledInput = enabledLocal
        apiKeyInput = apiKeyLocal
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151622)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "قسم التحكم باليوتيوب (سيرفر أونلاين)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "يسمح لك هذا القسم بتفعيل أو إلغاء تفعيل قسم اليوتيوب في الشريط السفلي للتطبيق، وتغيير مفتاح الـ API للبحث عبر خوادم يوتيوب أونلاين وثنائيات السيرفر.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF151622))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "تفعيل قسم اليوتيوب",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "عند تفعيل هذه الخانة سيظهر تطبيق يوتيوب مدمج في الشريط السفلي",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = enabledInput,
                    onCheckedChange = { enabledInput = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primaryColor
                    )
                )
            }
        }

        item {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("مفتاح YouTube API Key v3 (أونلاين)", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    isSaving = true
                    saveStatusMsg = null
                    viewModel.saveYoutubeRemoteConfig(enabledInput, apiKeyInput) { success ->
                        isSaving = false
                        isError = !success
                        saveStatusMsg = if (success) "تم الحفظ بنجاح ومزامنة السيرفر الأونلاين!" else "فشل الاتصال بقاعدة بيانات Firebase. تم الحفظ محلياً."
                        viewModel.searchYoutubeVideos("")
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("حفظ ومزامنة سيرفر Firebase ☁️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        saveStatusMsg?.let { msg ->
            item {
                Text(
                    text = msg,
                    color = if (isError) Color.Red else Color.Green,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
