package com.vk.personaltodo

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.vk.personaltodo.data.AppDatabase
import com.vk.personaltodo.data.Task
import com.vk.personaltodo.data.Expense
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var vm: TodoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.SCHEDULE_EXACT_ALARM), 101)
        }
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "personal_todo.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        vm = TodoViewModel(db)
        setContent { 
            DayFlowTheme {
                MainApp(vm) 
            }
        }
    }
}

private val DarkBluePrimary = Color(0xFF669DF6) // Modern light blue for primary
private val DarkBlueOnPrimary = Color(0xFF00315F)
private val DarkBluePrimaryContainer = Color(0xFF004786)
private val DarkBlueOnPrimaryContainer = Color(0xFFD1E4FF)

private val DarkBlueSecondary = Color(0xFF90B4CE) // Subtle blue gray
private val DarkBlueOnSecondary = Color(0xFF003258)
private val DarkBlueSecondaryContainer = Color(0xFF00497D)
private val DarkBlueOnSecondaryContainer = Color(0xFFD1E4FF)

private val DarkBlueTertiary = Color(0xFF3366CC) // Deep blue accent
private val DarkBlueOnTertiary = Color(0xFFFFFFFF)

private val DarkBackground = Color(0xFF0B0D12) // Very dark / near-black
private val DarkSurface = Color(0xFF13171F) // Deep black-blue surface
private val DarkSurfaceVariant = Color(0xFF1F2532) // Slightly lighter card color
private val DarkOnSurfaceVariant = Color(0xFFB8C0D0) // Readable text on variant

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)

private val DayFlowDarkColorScheme = darkColorScheme(
    primary = DarkBluePrimary,
    onPrimary = DarkBlueOnPrimary,
    primaryContainer = DarkBluePrimaryContainer,
    onPrimaryContainer = DarkBlueOnPrimaryContainer,
    secondary = DarkBlueSecondary,
    onSecondary = DarkBlueOnSecondary,
    secondaryContainer = DarkBlueSecondaryContainer,
    onSecondaryContainer = DarkBlueOnSecondaryContainer,
    tertiary = DarkBlueTertiary,
    onTertiary = DarkBlueOnTertiary,
    background = DarkBackground,
    onBackground = Color(0xFFE2E2E9),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError,
    outline = Color(0xFF8E95A6)
)

private val DayFlowLightColorScheme = lightColorScheme()

@Composable
fun DayFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DayFlowDarkColorScheme else DayFlowLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

class TodoViewModel(private val db: AppDatabase) : ViewModel() {
    val tasks = db.taskDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val expenses = db.expenseDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExpense(expense: Expense) = viewModelScope.launch {
        db.expenseDao().insert(expense)
    }
    
    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        db.expenseDao().delete(expense)
    }

    fun add(context: Context, task: Task) = viewModelScope.launch {
        val id = db.taskDao().insert(task)
        if (task.reminderAt != null) {
            ReminderScheduler.schedule(context, id, task.title, task.reminderAt)
        }
    }
    
    fun update(context: Context, task: Task) = viewModelScope.launch {
        db.taskDao().update(task)
        if (task.completed) {
            ReminderScheduler.cancel(context, task.id)
        } else if (task.reminderAt != null) {
            ReminderScheduler.schedule(context, task.id, task.title, task.reminderAt)
        } else {
            ReminderScheduler.cancel(context, task.id)
        }
    }

    fun delete(context: Context, task: Task) = viewModelScope.launch { 
        db.taskDao().delete(task)
        ReminderScheduler.cancel(context, task.id)
    }
    
    private fun moveDateKeepTime(originalMs: Long, targetDateCal: Calendar): Long {
        val orig = Calendar.getInstance().apply { timeInMillis = originalMs }
        val result = Calendar.getInstance().apply { timeInMillis = targetDateCal.timeInMillis }
        result.set(Calendar.HOUR_OF_DAY, orig.get(Calendar.HOUR_OF_DAY))
        result.set(Calendar.MINUTE, orig.get(Calendar.MINUTE))
        result.set(Calendar.SECOND, orig.get(Calendar.SECOND))
        result.set(Calendar.MILLISECOND, orig.get(Calendar.MILLISECOND))
        return result.timeInMillis
    }

    fun toggle(context: Context, task: Task) = viewModelScope.launch {
        val updated = task.copy(completed = !task.completed, completedAt = if (!task.completed) System.currentTimeMillis() else null)
        db.taskDao().update(updated)
        if (updated.completed) {
            ReminderScheduler.cancel(context, updated.id)
            
            if (updated.recurrence != "None" && updated.recurrence.isNotBlank()) {
                val nextCal = Calendar.getInstance()
                nextCal.add(Calendar.DAY_OF_YEAR, 1)
                val daysList = updated.recurrence.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                if (daysList.isNotEmpty()) {
                    var safeGuard = 0
                    while (!daysList.contains(nextCal.get(Calendar.DAY_OF_WEEK)) && safeGuard < 14) {
                        nextCal.add(Calendar.DAY_OF_YEAR, 1)
                        safeGuard++
                    }
                    val newCreatedAt = moveDateKeepTime(updated.createdAt, nextCal)
                    val newDueAt = updated.dueAt?.let { moveDateKeepTime(it, nextCal) }
                    val newReminderAt = updated.reminderAt?.let { moveDateKeepTime(it, nextCal) }
                    val newTask = updated.copy(
                        id = 0,
                        completed = false,
                        completedAt = null,
                        createdAt = newCreatedAt,
                        dueAt = newDueAt,
                        reminderAt = newReminderAt
                    )
                    val newId = db.taskDao().insert(newTask)
                    if (newTask.reminderAt != null) {
                        ReminderScheduler.schedule(context, newId, newTask.title, newTask.reminderAt)
                    }
                }
            }
        } else {
            // Un-completing: try to clean up spawned future instance
            if (updated.recurrence != "None" && updated.recurrence.isNotBlank()) {
                val futureTask = db.taskDao().findFutureRecurring(updated.title, updated.recurrence)
                if (futureTask != null) {
                    db.taskDao().delete(futureTask)
                    ReminderScheduler.cancel(context, futureTask.id)
                }
            }
            if (updated.reminderAt != null) {
                ReminderScheduler.schedule(context, updated.id, updated.title, updated.reminderAt)
            }
        }
    }
}

enum class SortOption(val title: String) {
    PRIORITY_DESC("Priority — High to Low"),
    PRIORITY_ASC("Priority — Low to High"),
    ALPHA_ASC("Alphabetical — A to Z"),
    ALPHA_DESC("Alphabetical — Z to A"),
    DATE_DESC("Date Created — Newest First"),
    DATE_ASC("Date Created — Oldest First")
}

enum class AppScreen { MAIN, DASHBOARD, TODO, EXPENSES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(vm: TodoViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    
    when (currentScreen) {
        AppScreen.MAIN -> MainMenuScreen(onNavigate = { currentScreen = it })
        AppScreen.DASHBOARD -> DashboardScreen(vm, onBack = { currentScreen = AppScreen.MAIN })
        AppScreen.TODO -> TodoApp(vm, onBack = { currentScreen = AppScreen.MAIN })
        AppScreen.EXPENSES -> ExpenseApp(vm, onBack = { currentScreen = AppScreen.MAIN })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(onNavigate: (AppScreen) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("DayFlow", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MenuButton("Dashboard", Icons.Default.Dashboard) { onNavigate(AppScreen.DASHBOARD) }
            Spacer(Modifier.height(16.dp))
            MenuButton("Todo", Icons.Default.CheckCircle) { onNavigate(AppScreen.TODO) }
            Spacer(Modifier.height(16.dp))
            MenuButton("Expense Tracker", Icons.Default.AttachMoney) { onNavigate(AppScreen.EXPENSES) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(vm: TodoViewModel, onBack: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    var selected by remember { mutableIntStateOf(0) }
    
    var showAdd by remember { mutableStateOf(value = false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var query by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val visible = remember(tasks, selected, query, sortOption) {
        val q = query.trim().lowercase()
        val filtered = tasks.filter {
            (q.isBlank() || it.title.lowercase().contains(q) || it.description.lowercase().contains(q)) &&
            when (selected) {
                0 -> !it.completed
                1 -> !it.completed && it.createdAt >= startOfToday() && it.createdAt <= endOfToday()
                2 -> !it.completed && it.recurrence != "None" && it.recurrence.isNotBlank()
                3 -> it.completed
                4 -> true // History
                else -> true
            }
        }
        if (selected == 4) {
            filtered.sortedByDescending { it.completedAt ?: it.createdAt }
        } else {
            when (sortOption) {
                SortOption.PRIORITY_DESC -> filtered.sortedWith(compareBy({ it.priority }, { -it.createdAt }))
                SortOption.PRIORITY_ASC -> filtered.sortedWith(compareBy({ -it.priority }, { -it.createdAt }))
                SortOption.ALPHA_ASC -> filtered.sortedBy { it.title.lowercase() }
                SortOption.ALPHA_DESC -> filtered.sortedByDescending { it.title.lowercase() }
                SortOption.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
                SortOption.DATE_ASC -> filtered.sortedBy { it.createdAt }
            }
        }
    }

    Scaffold(
        topBar = {
                TopAppBar(
                    title = { Text("My Tasks", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add task") }
            },
            bottomBar = {
                NavigationBar {
                            val navItems = listOf("Tasks", "Today", "Recurring", "Completed", "History")
                            navItems.forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = selected == i,
                                    onClick = { selected = i },
                                    icon = {
                                        val icon = when (i) {
                                            0 -> Icons.Default.CheckCircle
                                            1 -> Icons.Default.Today
                                            2 -> Icons.Default.Loop
                                            3 -> Icons.Default.DoneAll
                                            else -> Icons.Default.History
                                        }
                                        Icon(icon, label)
                                    },
                                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("Search tasks") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${visible.size} tasks", style = MaterialTheme.typography.titleMedium)
                        val done = tasks.count { it.completed }
                        Text("$done completed", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (selected != 4) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.title, fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { sortOption = option; showSortMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing here. Tap + to add a task.")
                    }
                } else if (selected == 4) {
                    val grouped = visible.groupBy { getHistoryGroupLabel(it.completedAt ?: it.createdAt) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grouped.forEach { (dateStr, tasksInGroup) ->
                            item(key = "header_$dateStr") {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }
                            items(tasksInGroup, key = { it.id }) { task ->
                                TaskCard(task, vm, onEdit = { editingTask = it })
                            }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visible, key = { it.id }) { task ->
                            TaskCard(task, vm, onEdit = { editingTask = it })
                        }
                    }
                }
            }
        }
        if (showAdd || editingTask != null) {
            val ctx = LocalContext.current
            TaskDialog(
                taskToEdit = editingTask,
                onDismiss = { showAdd = false; editingTask = null }
            ) { task ->
                if (editingTask == null) {
                    vm.add(ctx, task)
                } else {
                    vm.update(ctx, task)
                }
                showAdd = false
                editingTask = null
            }
        }
}

@Composable
fun TaskCard(task: Task, vm: TodoViewModel, onEdit: (Task) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completed, onCheckedChange = { vm.toggle(context, task) })
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodySmall)
                if (task.recurrence != "None" && task.recurrence.isNotBlank()) {
                    Text(formatRecurrence(task.recurrence), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(onClick = {}, label = { Text(task.category) })
                    val priorityText = when (task.priority) {
                        1 -> "HIGH"
                        2 -> "MEDIUM"
                        else -> "LOW"
                    }
                    val priorityColor = when (task.priority) {
                        1 -> MaterialTheme.colorScheme.error
                        2 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    Surface(
                        color = priorityColor.copy(alpha = 0.15f),
                        contentColor = priorityColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = priorityText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                task.dueAt?.let { Text("Due: ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Created: ${formatDate(task.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.completed && task.completedAt != null) {
                    Text("Completed: ${formatDate(task.completedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { onEdit(task); menu = false })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { vm.delete(context, task); menu = false })
            }
        }
    }
}

@Composable
fun TaskDialog(taskToEdit: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var desc by remember { mutableStateOf(taskToEdit?.description ?: "") }
    var category by remember { mutableStateOf(taskToEdit?.category ?: "Personal") }
    var priority by remember { mutableIntStateOf(taskToEdit?.priority ?: 2) }
    var reminderAt by remember { mutableStateOf<Long?>(taskToEdit?.reminderAt) }
    
    var showRecurrence by remember { mutableStateOf(taskToEdit?.recurrence != null && taskToEdit.recurrence != "None" && taskToEdit.recurrence.isNotBlank()) }
    var selectedDays by remember { mutableStateOf(
        if (taskToEdit?.recurrence != null && taskToEdit.recurrence != "None") {
            taskToEdit.recurrence.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        } else setOf()
    ) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (taskToEdit == null) "New task" else "Edit task") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true) }
                item { OutlinedTextField(desc, { desc = it }, label = { Text("Notes") }) }
                item {
                    Text("Priority")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1 to "High", 2 to "Medium", 3 to "Low").forEach { (p, s) ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(s) })
                        }
                    }
                }
                
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reminder")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val c = Calendar.getInstance()
                            if (reminderAt != null) c.timeInMillis = reminderAt!!
                            DatePickerDialog(context, { _, y, m, d ->
                                TimePickerDialog(context, { _, h, min ->
                                    val cal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                                    reminderAt = cal.timeInMillis
                                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                        }) {
                            Text(if (reminderAt != null) formatDate(reminderAt!!) else "No reminder")
                        }
                        if (reminderAt != null) {
                            IconButton(onClick = { reminderAt = null }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    }
                }
                
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Repeat")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showRecurrence = !showRecurrence }) {
                            Text(if (showRecurrence) "Custom..." else "Does not repeat")
                        }
                    }
                    if (showRecurrence) {
                        val days = listOf(
                            Calendar.MONDAY to "Monday",
                            Calendar.TUESDAY to "Tuesday",
                            Calendar.WEDNESDAY to "Wednesday",
                            Calendar.THURSDAY to "Thursday",
                            Calendar.FRIDAY to "Friday",
                            Calendar.SATURDAY to "Saturday",
                            Calendar.SUNDAY to "Sunday"
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            days.forEach { (calDay, name) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedDays.contains(calDay),
                                        onCheckedChange = { chk ->
                                            selectedDays = if (chk) selectedDays + calDay else selectedDays - calDay
                                        }
                                    )
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                item { OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true) }
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = {
                val recurrenceStr = if (showRecurrence && selectedDays.isNotEmpty()) selectedDays.joinToString(",") else "None"
                val t = Task(
                    id = taskToEdit?.id ?: 0,
                    title = title.trim(),
                    description = desc.trim(),
                    category = category.trim().ifBlank { "Personal" },
                    priority = priority,
                    reminderAt = reminderAt,
                    recurrence = recurrenceStr,
                    createdAt = taskToEdit?.createdAt ?: System.currentTimeMillis(),
                    completed = taskToEdit?.completed ?: false,
                    completedAt = taskToEdit?.completedAt,
                    dueAt = taskToEdit?.dueAt
                )
                onSave(t)
            }) { Text(if (taskToEdit == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun getHistoryGroupLabel(ms: Long): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val today = cal.timeInMillis
    val yesterday = today - 86400000L
    
    val taskCal = Calendar.getInstance().apply { timeInMillis = ms }
    val currentCal = Calendar.getInstance()
    
    return when {
        ms >= today -> "Today"
        ms >= yesterday -> "Yesterday"
        currentCal.get(Calendar.YEAR) == taskCal.get(Calendar.YEAR) && currentCal.get(Calendar.MONTH) == taskCal.get(Calendar.MONTH) -> 
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ms))
    }
}

fun startOfToday(): Long {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return c.timeInMillis
}

fun endOfToday(): Long {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
    return c.timeInMillis
}

fun formatRecurrence(recurrence: String): String {
    if (recurrence == "None" || recurrence.isBlank()) return ""
    val days = recurrence.split(",").mapNotNull { it.toIntOrNull() }.sorted()
    if (days.size == 7) return "Every day"
    val names = days.map {
        when (it) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            else -> ""
        }
    }.filter { it.isNotEmpty() }
    if (names.isEmpty()) return ""
    return "Every " + names.joinToString(", ")
}

fun formatDate(ms: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
