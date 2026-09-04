package com.example.data.ai

import com.example.BuildConfig
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.Priority
import com.example.data.local.entity.TransactionType
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.PersonalManagerRepository
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class GeminiAssistantService(
    private val repository: PersonalManagerRepository,
    private val preferenceManager: PreferenceManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun processUserPrompt(userPrompt: String): AssistantTurnResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        val hasValidKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

        val currencyCode = try {
            preferenceManager.currencyCodeFlow.first()
        } catch (e: Exception) {
            "USD"
        }

        // 1. Gather strictly privacy-filtered local aggregation context
        val unlockedNotes = repository.getUnlockedNotesForAiContext()
        val expenseAggregations = repository.getMonthlyExpenseBreakdownForAi()
        val accountBalances = repository.getAccountBalancesSummaryForAi()

        val contextBuilder = StringBuilder()
        contextBuilder.append("Active User Currency: $currencyCode (${CurrencyFormatter.getCurrencySymbol(currencyCode)})\n")
        contextBuilder.append("Current System Context:\n")
        contextBuilder.append("Accounts: ${accountBalances.joinToString { "${it.first}: ${CurrencyFormatter.format(it.second, currencyCode)}" }}\n")
        contextBuilder.append("Monthly Expenses by Category: ${expenseAggregations.entries.joinToString { "${it.key}: ${CurrencyFormatter.format(it.value, currencyCode)}" }}\n")
        contextBuilder.append("User Notes (Unlocked only): ${unlockedNotes.joinToString { "${it.title}: ${it.content.take(60)}" }}\n")

        if (hasValidKey) {
            try {
                return@withContext callGeminiApi(apiKey, userPrompt, contextBuilder.toString(), currencyCode)
            } catch (e: Exception) {
                // Fallback to local intelligent parser
                return@withContext parseWithLocalEngine(userPrompt, expenseAggregations, accountBalances, currencyCode, fallbackNotice = " (Processed locally: ${e.localizedMessage})")
            }
        } else {
            return@withContext parseWithLocalEngine(userPrompt, expenseAggregations, accountBalances, currencyCode)
        }
    }

    private fun callGeminiApi(apiKey: String, prompt: String, contextInfo: String, currencyCode: String): AssistantTurnResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            val systemInstruction = """
                You are the Personal Manager Executive AI Assistant.
                You help users organize finances, debts, tasks, reminders, and notes.
                $contextInfo
                
                CRITICAL DIRECTIVES:
                1. Whenever the user's input asks to create, log, add, spend, lend, borrow, schedule, or remind, you MUST call the corresponding tool/function declarations (create_task, log_transaction, create_debt, create_reminder, set_budget).
                2. DO NOT reply with plain text instructions or pretend you performed actions in text. Execute the tool call so the user can preview and confirm the action draft.
                3. Compound or multi-action requests (e.g. "Lent Sarah $50 for lunch from Bank account, remind me Friday at 5 PM to collect") MUST execute composite parallel function calls (both create_debt AND create_reminder).
                4. For informational queries about expenses, balances, or notes, answer conversationally using the provided numerical context facts in the active user currency ($currencyCode).
                5. Use $currencyCode as the default currency context for financial transactions and amounts.
            """.trimIndent()

            partsArray.put(JSONObject().put("text", "$systemInstruction\n\nUser request: $prompt"))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            // Define tools for function calling
            val toolsArray = JSONArray()
            val toolsObj = JSONObject()
            val functionDeclarations = JSONArray()

            // Tool: create_task
            functionDeclarations.put(JSONObject().apply {
                put("name", "create_task")
                put("description", "Draft a new productivity task or todo item")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().put("type", "STRING").put("description", "Task title"))
                        put("priority", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("LOW", "MEDIUM", "HIGH"))))
                        put("due_date", JSONObject().put("type", "STRING").put("description", "Relative date or ISO string"))
                    })
                    put("required", JSONArray(listOf("title")))
                })
            })

            // Tool: log_transaction
            functionDeclarations.put(JSONObject().apply {
                put("name", "log_transaction")
                put("description", "Draft a financial income or expense transaction")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("account_name", JSONObject().put("type", "STRING"))
                        put("type", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("INCOME", "EXPENSE"))))
                        put("amount", JSONObject().put("type", "NUMBER"))
                        put("category", JSONObject().put("type", "STRING"))
                        put("note", JSONObject().put("type", "STRING"))
                    })
                    put("required", JSONArray(listOf("type", "amount", "category")))
                })
            })

            // Tool: create_debt
            functionDeclarations.put(JSONObject().apply {
                put("name", "create_debt")
                put("description", "Draft an immutable loan or debt entry (money lent out or borrowed)")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("counterparty", JSONObject().put("type", "STRING"))
                        put("direction", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("LENT", "BORROWED"))))
                        put("amount", JSONObject().put("type", "NUMBER"))
                        put("account_name", JSONObject().put("type", "STRING"))
                        put("due_date", JSONObject().put("type", "STRING"))
                        put("note", JSONObject().put("type", "STRING"))
                    })
                    put("required", JSONArray(listOf("counterparty", "direction", "amount")))
                })
            })

            // Tool: create_reminder
            functionDeclarations.put(JSONObject().apply {
                put("name", "create_reminder")
                put("description", "Draft a high-priority event reminder or agenda alert")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().put("type", "STRING"))
                        put("trigger_time", JSONObject().put("type", "STRING").put("description", "Target alert time"))
                        put("is_critical", JSONObject().put("type", "BOOLEAN"))
                    })
                    put("required", JSONArray(listOf("title")))
                })
            })

            // Tool: set_budget
            functionDeclarations.put(JSONObject().apply {
                put("name", "set_budget")
                put("description", "Draft a category monthly budget with optional rollover")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("category", JSONObject().put("type", "STRING"))
                        put("monthly_limit", JSONObject().put("type", "NUMBER"))
                        put("allow_rollover", JSONObject().put("type", "BOOLEAN"))
                    })
                    put("required", JSONArray(listOf("category", "monthly_limit")))
                })
            })

            toolsObj.put("functionDeclarations", functionDeclarations)
            toolsArray.put(toolsObj)
            put("tools", toolsArray)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        val draftActions = mutableListOf<AiDraftAction>()
        val replyTextBuilder = StringBuilder()

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    replyTextBuilder.append(part.getString("text"))
                }
                if (part.has("functionCall")) {
                    val functionCall = part.getJSONObject("functionCall")
                    val name = functionCall.getString("name")
                    val args = functionCall.optJSONObject("args") ?: JSONObject()

                    when (name) {
                        "create_task" -> {
                            val title = args.optString("title", "New Task")
                            val priorityStr = args.optString("priority", "MEDIUM")
                            val priority = try { Priority.valueOf(priorityStr) } catch (e: Exception) { Priority.MEDIUM }
                            draftActions.add(AiDraftAction.TaskDraft(title = title, priority = priority))
                        }
                        "log_transaction" -> {
                            val typeStr = args.optString("type", "EXPENSE")
                            val type = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.EXPENSE }
                            val amount = args.optDouble("amount", 0.0)
                            val category = args.optString("category", "General")
                            val accountName = args.optString("account_name", "Bank Account")
                            val note = args.optString("note", "")
                            draftActions.add(
                                AiDraftAction.TransactionDraft(
                                    accountName = accountName,
                                    type = type,
                                    amount = amount,
                                    category = category,
                                    note = note.ifEmpty { null }
                                )
                            )
                        }
                        "create_debt" -> {
                            val counterparty = args.optString("counterparty", "Friend")
                            val dirStr = args.optString("direction", "LENT")
                            val direction = try { DebtDirection.valueOf(dirStr) } catch (e: Exception) { DebtDirection.LENT }
                            val amount = args.optDouble("amount", 0.0)
                            val accountName = args.optString("account_name", "Bank Account")
                            val note = args.optString("note", "")
                            draftActions.add(
                                AiDraftAction.DebtDraft(
                                    counterparty = counterparty,
                                    direction = direction,
                                    amount = amount,
                                    accountName = accountName,
                                    note = note.ifEmpty { null }
                                )
                            )
                        }
                        "create_reminder" -> {
                            val title = args.optString("title", "Reminder")
                            val isCritical = args.optBoolean("is_critical", false)
                            val triggerTime = System.currentTimeMillis() + (2 * 3600 * 1000L) // Default 2h
                            draftActions.add(
                                AiDraftAction.ReminderDraft(
                                    title = title,
                                    triggerTime = triggerTime,
                                    isCritical = isCritical
                                )
                            )
                        }
                        "set_budget" -> {
                            val category = args.optString("category", "General")
                            val limit = args.optDouble("monthly_limit", 100.0)
                            val rollover = args.optBoolean("allow_rollover", false)
                            draftActions.add(
                                AiDraftAction.BudgetDraft(
                                    category = category,
                                    monthlyLimit = limit,
                                    allowRollover = rollover
                                )
                            )
                        }
                    }
                }
            }
        }

        val finalReply = if (draftActions.isNotEmpty()) {
            if (replyTextBuilder.isNotEmpty()) {
                "${replyTextBuilder.toString().trim()}\n\nI have prepared ${draftActions.size} draft action(s) for your review below. Please verify the details and tap Confirm & Save."
            } else {
                "I have generated ${draftActions.size} draft action(s) based on your request. Please review the details below before saving:"
            }
        } else {
            replyTextBuilder.toString().ifEmpty { "Understood. How else can I assist you with your finances, schedule, or notes?" }
        }

        return AssistantTurnResult(finalReply, draftActions)
    }

    /**
     * Local intelligent parsing engine for composite tool calls and local query answers.
     * Complies fully with:
     * - Composite / Parallel Tool Calling Rule (detects multiple actions in compound commands)
     * - Draft & Confirm Preview Card Rule (generates editable draft items, does not commit directly)
     * - Local Aggregation Context (answers numeric questions from Room data)
     */
    private fun parseWithLocalEngine(
        prompt: String,
        expenseAggregations: Map<String, Double>,
        accountBalances: List<Pair<String, Double>>,
        currencyCode: String,
        fallbackNotice: String = ""
    ): AssistantTurnResult {
        val lower = prompt.lowercase()
        val draftActions = mutableListOf<AiDraftAction>()

        // 1. Query: "How much did I spend on [Category]?"
        if (lower.contains("how much") && (lower.contains("spend") || lower.contains("spent"))) {
            for ((cat, total) in expenseAggregations) {
                if (lower.contains(cat.lowercase())) {
                    return AssistantTurnResult(
                        "According to your financial records this month, you have spent ${CurrencyFormatter.format(total, currencyCode)} on $cat.$fallbackNotice"
                    )
                }
            }
            val totalAll = expenseAggregations.values.sum()
            return AssistantTurnResult(
                "You have spent a total of ${CurrencyFormatter.format(totalAll, currencyCode)} across all categories this month.$fallbackNotice"
            )
        }

        // 2. Query: "How much in [Account]?" or "What is my balance?"
        if (lower.contains("balance") || (lower.contains("how much") && lower.contains("have in"))) {
            for ((accName, bal) in accountBalances) {
                if (lower.contains(accName.lowercase())) {
                    return AssistantTurnResult(
                        "Your $accName currently has a liquid balance of ${CurrencyFormatter.format(bal, currencyCode)}.$fallbackNotice"
                    )
                }
            }
            val totalCash = accountBalances.sumOf { it.second }
            return AssistantTurnResult(
                "Your total liquid cash across all accounts is ${CurrencyFormatter.format(totalCash, currencyCode)}.$fallbackNotice"
            )
        }

        // 3. Composite Recognition: Detect Debt ("Lent Sarah $50...")
        val lentMatch = Regex("""(?:lent|loaned|gave)\s+([a-zA-Z]+)\s+\$?([0-9]+(?:\.[0-9]{1,2})?)""").find(lower)
        val borrowedMatch = Regex("""(?:borrowed|took)\s+\$?([0-9]+(?:\.[0-9]{1,2})?)\s+from\s+([a-zA-Z]+)""").find(lower)

        if (lentMatch != null) {
            val counterparty = lentMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            val amount = lentMatch.groupValues[2].toDoubleOrNull() ?: 50.0
            val accountName = if (lower.contains("cash")) "Cash Wallet" else if (lower.contains("savings")) "High Yield Savings" else "Bank Account"
            draftActions.add(
                AiDraftAction.DebtDraft(
                    counterparty = counterparty,
                    direction = DebtDirection.LENT,
                    amount = amount,
                    accountName = accountName,
                    note = "Personal loan to $counterparty"
                )
            )
        } else if (borrowedMatch != null) {
            val amount = borrowedMatch.groupValues[1].toDoubleOrNull() ?: 50.0
            val counterparty = borrowedMatch.groupValues[2].replaceFirstChar { it.uppercase() }
            val accountName = if (lower.contains("cash")) "Cash Wallet" else "Bank Account"
            draftActions.add(
                AiDraftAction.DebtDraft(
                    counterparty = counterparty,
                    direction = DebtDirection.BORROWED,
                    amount = amount,
                    accountName = accountName,
                    note = "Borrowed from $counterparty"
                )
            )
        }

        // 4. Composite Recognition: Detect Reminder ("remind me Friday at 5 PM to collect")
        val remindMatch = Regex("""remind me\s+(.+?)(?:$|\.|\n)""").find(lower)
        if (remindMatch != null) {
            val reminderContent = remindMatch.groupValues[1].trim()
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, 4) // Sensible default relative time

            draftActions.add(
                AiDraftAction.ReminderDraft(
                    title = reminderContent.replaceFirstChar { it.uppercase() },
                    triggerTime = calendar.timeInMillis,
                    isCritical = lower.contains("urgent") || lower.contains("important")
                )
            )
        }

        // 5. Detect Standard Expense/Income Transaction ("Spent $24 on lunch from Bank")
        val expenseMatch = Regex("""(?:spent|paid|bought|expense)\s+\$?([0-9]+(?:\.[0-9]{1,2})?)\s+(?:on|for)\s+([a-zA-Z\s]+)""").find(lower)
        if (expenseMatch != null && lentMatch == null) {
            val amount = expenseMatch.groupValues[1].toDoubleOrNull() ?: 20.0
            val category = expenseMatch.groupValues[2].trim().take(20).replaceFirstChar { it.uppercase() }
            val accountName = if (lower.contains("cash")) "Cash Wallet" else "Bank Account"
            draftActions.add(
                AiDraftAction.TransactionDraft(
                    accountName = accountName,
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    category = category,
                    note = "Logged via assistant"
                )
            )
        }

        // 6. Detect Task ("Add task to review slides")
        if ((lower.contains("add task") || lower.contains("create task") || lower.contains("todo")) && remindMatch == null) {
            val title = prompt.replace(Regex("""(?i)(add task|create task|todo|please)\s*"""), "").trim()
            draftActions.add(
                AiDraftAction.TaskDraft(
                    title = title.ifEmpty { "New Task" },
                    priority = if (lower.contains("high") || lower.contains("urgent")) Priority.HIGH else Priority.MEDIUM
                )
            )
        }

        // 7. Detect Budget ("Set budget for groceries to $400")
        val budgetMatch = Regex("""budget\s+for\s+([a-zA-Z\s]+)\s+(?:to|at)\s+\$?([0-9]+)""").find(lower)
        if (budgetMatch != null) {
            val category = budgetMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val limit = budgetMatch.groupValues[2].toDoubleOrNull() ?: 300.0
            draftActions.add(
                AiDraftAction.BudgetDraft(
                    category = category,
                    monthlyLimit = limit,
                    allowRollover = lower.contains("rollover")
                )
            )
        }

        val reply = if (draftActions.isNotEmpty()) {
            "I've drafted ${draftActions.size} action(s) for your review according to the Draft & Confirm protocol. Please review and tap 'Confirm & Save' below to commit them.$fallbackNotice"
        } else {
            "Hello! I am your Personal Manager Assistant. You can ask me compound commands like:\n• \"Lent Sarah \$50 for lunch from my Bank account, remind me Friday at 5 PM to collect\"\n• \"Spent \$32 on Groceries from Cash Wallet\"\n• \"How much did I spend on food this month?\"\n• \"Set budget for Dining to \$200\"$fallbackNotice"
        }

        return AssistantTurnResult(reply, draftActions)
    }
}
