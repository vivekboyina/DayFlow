package com.vk.personaltodo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: TodoViewModel, onBack: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val expenses by vm.expenses.collectAsState()

    val totalPending = tasks.count { !it.completed }
    val totalCompleted = tasks.count { it.completed }
    
    val startOfToday = remember { startOfToday() }
    val endOfToday = remember { endOfToday() }
    
    val createdToday = tasks.count { it.createdAt in startOfToday..endOfToday }
    val completedToday = tasks.count { it.completed && it.completedAt != null && it.completedAt in startOfToday..endOfToday }
    
    val totalTasks = totalPending + totalCompleted
    val completionPercentage = if (totalTasks > 0) (totalCompleted.toFloat() / totalTasks * 100).toInt() else 0

    // Recurring Tasks
    val recurringTasks = tasks.filter { !it.completed && it.recurrence != "None" && it.recurrence.isNotBlank() }
    val nextRecurring = recurringTasks.minByOrNull { it.createdAt }

    // Expenses
    val cal = Calendar.getInstance()
    val startOfMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
    val startOfWeek = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis

    val monthExpenses = expenses.filter { it.date >= startOfMonth }
    val weekExpenses = expenses.filter { it.date >= startOfWeek }
    val todayExpenses = expenses.filter { it.date >= startOfToday }

    val spentThisMonth = monthExpenses.sumOf { it.amount }
    val spentThisWeek = weekExpenses.sumOf { it.amount }
    val spentToday = todayExpenses.sumOf { it.amount }

    val catTotals = monthExpenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
    val topCategory = catTotals.maxByOrNull { it.value }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tasks Analytics
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("TASKS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    if (totalTasks == 0) {
                        Text("No tasks yet.")
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending")
                            Text(totalPending.toString(), fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Completed")
                            Text(totalCompleted.toString(), fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Created Today")
                            Text(createdToday.toString(), fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Completed Today")
                            Text(completedToday.toString(), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Completion ($completionPercentage%)", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(
                            progress = { if (totalTasks > 0) totalCompleted.toFloat() / totalTasks else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                    }
                }
            }

            // Recurring Tasks
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("RECURRING", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Active recurring tasks: ${recurringTasks.size}")
                    if (nextRecurring != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Next:", fontWeight = FontWeight.SemiBold)
                        Text(nextRecurring.title, color = MaterialTheme.colorScheme.primary)
                        Text(formatRecurrence(nextRecurring.recurrence), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Expenses Analytics
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
                    Text("EXPENSES — ${monthName.uppercase(Locale.getDefault())}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    
                    if (expenses.isEmpty()) {
                        Text("No expenses recorded yet.")
                    } else {
                        Text("Total Spent", style = MaterialTheme.typography.labelMedium)
                        Text("₹${String.format(Locale.getDefault(), "%.0f", spentThisMonth)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("This Week", style = MaterialTheme.typography.labelSmall)
                                Text("₹${String.format(Locale.getDefault(), "%.0f", spentThisWeek)}", fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Today", style = MaterialTheme.typography.labelSmall)
                                Text("₹${String.format(Locale.getDefault(), "%.0f", spentToday)}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        if (topCategory != null) {
                            Spacer(Modifier.height(12.dp))
                            Text("Top Category", style = MaterialTheme.typography.labelMedium)
                            Text("${topCategory.key} — ₹${String.format(Locale.getDefault(), "%.0f", topCategory.value)}", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Category Breakdown
            if (monthExpenses.isNotEmpty()) {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CATEGORY BREAKDOWN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        
                        EXPENSE_CATEGORIES.forEach { cat ->
                            val total = catTotals[cat] ?: 0.0
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cat, style = MaterialTheme.typography.bodyMedium)
                                Text("₹${String.format(Locale.getDefault(), "%.0f", total)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Monthly Overview
            if (expenses.isNotEmpty()) {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("MONTHLY OVERVIEW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        
                        val monthlyTotals = expenses.groupBy { 
                            val c = Calendar.getInstance().apply { timeInMillis = it.date }
                            c.set(Calendar.DAY_OF_MONTH, 1)
                            c.set(Calendar.HOUR_OF_DAY, 0)
                            c.set(Calendar.MINUTE, 0)
                            c.timeInMillis
                        }.mapValues { it.value.sumOf { e -> e.amount } }
                        
                        monthlyTotals.toSortedMap().forEach { (dateMs, total) ->
                            val monthStr = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(dateMs))
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(monthStr, style = MaterialTheme.typography.bodyMedium)
                                Text("₹${String.format(Locale.getDefault(), "%.0f", total)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Recent Activity
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("RECENT ACTIVITY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    
                    val recentCompleted = tasks.filter { it.completed && it.completedAt != null }.sortedByDescending { it.completedAt }.take(2)
                    val recentCreated = tasks.sortedByDescending { it.createdAt }.take(2)
                    val recentExpenses = expenses.sortedByDescending { it.date }.take(2)

                    if (recentCompleted.isEmpty() && recentCreated.isEmpty() && recentExpenses.isEmpty()) {
                        Text("No recent activity.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        recentCompleted.forEach {
                            Text("✓ Completed: ${it.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        recentCreated.forEach {
                            Text("+ Added: ${it.title}", style = MaterialTheme.typography.bodyMedium)
                        }
                        recentExpenses.forEach {
                            Text("- Spent ₹${String.format(Locale.getDefault(), "%.0f", it.amount)} on ${it.description}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
