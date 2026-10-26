package com.voicereminder.vip.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.data.db.CategoryEntity
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.AppTopBar
import com.voicereminder.vip.ui.components.ConfirmDialog
import com.voicereminder.vip.ui.components.EmptyState
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.categoryIcon
import com.voicereminder.vip.ui.components.parseColor
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.vm.AppViewModel

private val palette = listOf(
    "#F2C14E", "#5FD3C4", "#7FB2FF", "#FF8A9B", "#B9A7FF",
    "#FFB86B", "#8BE28B", "#E0A6FF", "#9AD7C7", "#FFD166"
)

private val iconKeys = listOf(
    "sun", "school", "work", "medication", "event", "cart",
    "fitness", "home", "prayer", "person", "tasks", "night", "favorite", "list"
)

@Composable
fun CategoriesScreen(app: AppViewModel, onBack: () -> Unit) {
    val categories by app.categories.collectAsStateWithLifecycle()
    val alarms by app.alarms.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "التصنيفات",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            editing = null
                            showEditor = true
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "إضافة تصنيف", tint = Gold)
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "نظّم منبهاتك بألوان وأيقونات، ثم صفّها من الشاشة الرئيسية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (categories.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Category,
                            title = "لا توجد تصنيفات",
                            message = "أضف تصنيفًا لتجميع تذكيراتك.",
                            actionLabel = "إضافة تصنيف",
                            onAction = {
                                editing = null
                                showEditor = true
                            }
                        )
                    }
                } else {
                    items(categories, key = { it.id }) { category ->
                        val count = alarms.count { it.categoryId == category.id }
                        CategoryRow(
                            category = category,
                            alarmCount = count,
                            onEdit = {
                                editing = category
                                showEditor = true
                            },
                            onDelete = { pendingDelete = category }
                        )
                    }
                }

                item {
                    InfoNote(
                        "حذف تصنيف لا يحذف منبهاته؛ تصبح بدون تصنيف. التصنيفات الافتراضية محمية من الحذف."
                    )
                }
            }
        }
    }

    if (showEditor) {
        CategoryEditorDialog(
            category = editing,
            onDismiss = { showEditor = false },
            onSave = { name, colorHex, iconKey ->
                val target = editing
                val entity = if (target == null) {
                    CategoryEntity(name = name, colorHex = colorHex, iconKey = iconKey)
                } else {
                    target.copy(name = name, colorHex = colorHex, iconKey = iconKey)
                }
                app.saveCategory(entity) { error ->
                    if (error == null) showEditor = false
                }
            }
        )
    }

    pendingDelete?.let { category ->
        ConfirmDialog(
            title = "حذف التصنيف؟",
            text = "سيُحذف «${category.name}» وستصبح منبهاته بدون تصنيف.",
            onConfirm = {
                app.deleteCategory(category)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    alarmCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = parseColor(category.colorHex)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.26f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    categoryIcon(category.iconKey),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$alarmCount منبه" + if (category.isDefault) " • افتراضي" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "تعديل",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (!category.isDefault) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String, iconKey: String) -> Unit
) {
    var name by remember(category?.id) { mutableStateOf(category?.name ?: "") }
    var colorHex by remember(category?.id) { mutableStateOf(category?.colorHex ?: palette.first()) }
    var iconKey by remember(category?.id) { mutableStateOf(category?.iconKey ?: "list") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "تصنيف جديد" else "تعديل التصنيف") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); error = null },
                    singleLine = true,
                    label = { Text("الاسم") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("اللون", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(palette) { hex ->
                        val selected = colorHex == hex
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { colorHex = hex },
                            shape = CircleShape,
                            color = parseColor(hex),
                            border = if (selected) {
                                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                            } else null
                        ) {}
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("الأيقونة", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(iconKeys) { key ->
                        val selected = iconKey == key
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (selected) parseColor(colorHex).copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                categoryIcon(key),
                                contentDescription = key,
                                tint = if (selected) parseColor(colorHex)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "اكتب اسم التصنيف."
                        return@Button
                    }
                    onSave(name.trim(), colorHex, iconKey)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Color(0xFF251A00)
                )
            ) {
                Text("حفظ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
