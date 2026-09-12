package com.vk.personaltodo

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vk.personaltodo.data.Expense
import java.text.SimpleDateFormat
import java.util.*

val EXPENSE_CATEGORIES = listOf(
    "🍔 Food",
    "🚌 Transport",
    "📚 Education",
    "🛍️ Shopping",
    "🎮 Entertainment",
    "💻 Subscriptions",
    "🏠 Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseApp(vm: TodoViewModel, onBack: () -> Unit) {
    val expenses by vm.expenses.collectAsState()
    
    // For navigating between months
    var currentMonthCal by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    
    var showAdd by remember { mutableStateOf(false) }
    
    // Filter expenses for selected month
    val monthExpenses = remember(expenses, currentMonthCal) {
        val startMs = currentMonthCal.timeInMillis
        val endCal = Calendar.getInstance().apply { 
            timeInMillis = currentMonthCal.timeInMillis
            add(Calendar.MONTH, 1)
        }
        val endMs = endCal.timeInMillis
        expenses.filter { it.date in startMs until endMs }
    }
    
    val categoryTotals = remember(monthExpenses) {
        EXPENSE_CATEGORIES.associateWith { cat ->
            monthExpenses.filter { it.category == cat }.sumOf { it.amount }
        }
    }
    val grandTotal = categoryTotals.values.sum()

    val monthYearStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(currentMonthCal.timeInMillis))

    Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Expense Tracker", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add Expense") }
            }
        ) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
                
                // Month Navigation
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        val newCal = Calendar.getInstance().apply { timeInMillis = currentMonthCal.timeInMillis; add(Calendar.MONTH, -1) }
                        currentMonthCal = newCal
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous Month")
                    }
                    Text(monthYearStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { 
                        val newCal = Calendar.getInstance().apply { timeInMillis = currentMonthCal.timeInMillis; add(Calendar.MONTH, 1) }
                        currentMonthCal = newCal
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next Month")
                    }
                }
                
                // Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Monthly Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        EXPENSE_CATEGORIES.forEach { cat ->
                            val total = categoryTotals[cat] ?: 0.0
                            if (total > 0) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cat, style = MaterialTheme.typography.bodyMedium)
                                    Text("₹${String.format(Locale.getDefault(), "%.0f", total)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (grandTotal == 0.0) {
                            Text("No expenses this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Spent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("₹${String.format(Locale.getDefault(), "%.0f", grandTotal)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Text("Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                if (monthExpenses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No transactions found.")
                    }
                } else {
                    val grouped = monthExpenses.groupBy { 
                        val c = Calendar.getInstance().apply { timeInMillis = it.date }
                        c.set(Calendar.HOUR_OF_DAY, 0)
                        c.set(Calendar.MINUTE, 0)
                        c.set(Calendar.SECOND, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        c.timeInMillis
                    }.toSortedMap(compareByDescending { it })
                    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grouped.forEach { (dateMs, expensesList) ->
                            item {
                                val dateStr = SimpleDateFormat("MMMM dd", Locale.getDefault()).format(Date(dateMs))
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(expensesList, key = { it.id }) { exp ->
                                ExpenseCard(exp, vm)
                            }
                        }
                    }
                }
            }
        }
        
        if (showAdd) {
            AddExpenseDialog(onDismiss = { showAdd = false }) { exp ->
                vm.addExpense(exp)
                showAdd = false
            }
        }
}

@Composable
fun ExpenseCard(expense: Expense, vm: TodoViewModel) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(expense.description, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(expense.category, style = MaterialTheme.typography.bodySmall)
            }
            Text("₹${String.format(Locale.getDefault(), "%.0f", expense.amount)}", fontWeight = FontWeight.Bold)
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { vm.deleteExpense(expense); menu = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EXPENSE_CATEGORIES[0]) }
    var dateMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var expandedCat by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Expense") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        singleLine = true
                    )
                }
                
                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedCat,
                        onExpandedChange = { expandedCat = !expandedCat }
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCat,
                            onDismissRequest = { expandedCat = false }
                        ) {
                            EXPENSE_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = { category = cat; expandedCat = false }
                                )
                            }
                        }
                    }
                }
                
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Date")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = dateMs }
                            DatePickerDialog(context, { _, y, m, d ->
                                val cal = Calendar.getInstance().apply { set(y, m, d) }
                                dateMs = cal.timeInMillis
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                        }) {
                            Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dateMs)))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = amount.toDoubleOrNull() != null && desc.isNotBlank(),
                onClick = {
                    onSave(
                        Expense(
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            category = category,
                            description = desc.trim(),
                            date = dateMs
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
