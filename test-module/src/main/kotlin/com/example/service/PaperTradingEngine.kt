package com.example.service

import com.example.model.GannLevels
import com.example.model.TradeState
import org.json.JSONObject
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaperTradingEngine(
    private val symbolsGannMap: Map<String, GannLevels>,
    private val quantitiesMap: Map<String, Int>
) {
    private val stateFile = File("active_trades.json")
    private val tradesMap = mutableMapOf<String, TradeState>()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        loadState()
        for ((symbol, gann) in symbolsGannMap) {
            val existing = tradesMap[symbol]
            val qty = quantitiesMap[symbol] ?: 1
            if (existing == null) {
                tradesMap[symbol] = TradeState(
                    symbol = symbol,
                    status = "IDLE",
                    stopLoss = gann.stopLoss,
                    entryPrice = gann.buyPrice,
                    targets = listOf(
                        gann.target1, gann.target2, gann.target3,
                        gann.target4, gann.target5, gann.target6,
                        gann.target7, gann.target8, gann.target9, gann.target10
                    ),
                    quantity = qty
                )
            } else {
                existing.entryPrice = gann.buyPrice
                if (existing.status == "IDLE") {
                    existing.stopLoss = gann.stopLoss
                }
                existing.targets = listOf(
                    gann.target1, gann.target2, gann.target3,
                    gann.target4, gann.target5, gann.target6,
                    gann.target7, gann.target8, gann.target9, gann.target10
                )
            }
        }
    }

    fun onTick(symbol: String, ltp: Double) {
        val trade = tradesMap[symbol] ?: return
        val gann = symbolsGannMap[symbol] ?: return

        // Check EOD Auto Square-Off at 3:25 PM IST
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = LocalTime.now(istZone)
        val squareOffTime = LocalTime.of(15, 25)
        if (now.isAfter(squareOffTime) || now == squareOffTime) {
            if (trade.status == "ACTIVE") {
                val pnl = (ltp - trade.entryPrice) * trade.quantity
                val msg = "[AUTO SQUARE-OFF] 3:25 PM EOD reached! Force exiting $symbol at LTP $ltp | P&L: ${String.format("%.2f", pnl)}"
                println(msg)
                scope.launch { TelegramNotifier.sendAlert(msg) }
                
                trade.status = "EXITED"
                saveState()
                checkAndSendEodSummary()
            }
            return
        }

        when (trade.status) {
            "IDLE" -> {
                if (ltp >= trade.entryPrice) {
                    val msg = "[TRADE PLACED - LIMIT BUY] $symbol at Entry: ${trade.entryPrice} | Qty: ${trade.quantity} | LTP: $ltp"
                    println(msg)
                    scope.launch { TelegramNotifier.sendAlert(msg) }

                    trade.status = "ACTIVE"
                    trade.currentTpLevel = 0
                    trade.entryTime = System.currentTimeMillis()
                    saveState()
                } else {
                    val msg = "[TRADE PLACED - STOP BUY] $symbol at Entry: ${trade.entryPrice} | Qty: ${trade.quantity} | LTP: $ltp"
                    println(msg)
                    scope.launch { TelegramNotifier.sendAlert(msg) }

                    trade.status = "ACTIVE"
                    trade.currentTpLevel = 0
                    trade.entryTime = System.currentTimeMillis()
                    saveState()
                }
            }
            "ACTIVE" -> {
                // 1. Check Stop Loss
                if (ltp <= trade.stopLoss) {
                    val pnl = (trade.stopLoss - trade.entryPrice) * trade.quantity
                    val msg = "[STOP LOSS HIT] $symbol exited at SL: ${trade.stopLoss} | LTP: $ltp | Est P&L: ${String.format("%.2f", pnl)}"
                    println(msg)
                    scope.launch { TelegramNotifier.sendAlert(msg) }

                    trade.status = "EXITED"
                    saveState()
                    return
                }

                // 2. Check Targets & Trailing SL
                val targets = trade.targets
                if (trade.currentTpLevel < targets.size) {
                    val nextTarget = targets[trade.currentTpLevel]
                    if (ltp >= nextTarget) {
                        trade.currentTpLevel++
                        val msg = if (trade.currentTpLevel == 1) {
                            trade.stopLoss = trade.entryPrice
                            "[TARGET ${trade.currentTpLevel} HIT] $symbol reached TP1 ($nextTarget)! Trailing SL moved to Entry: ${trade.stopLoss}"
                        } else {
                            trade.stopLoss = targets[trade.currentTpLevel - 2]
                            "[TARGET ${trade.currentTpLevel} HIT] $symbol reached TP${trade.currentTpLevel} ($nextTarget)! Trailing SL moved to TP${trade.currentTpLevel - 1}: ${trade.stopLoss}"
                        }
                        println(msg)
                        scope.launch { TelegramNotifier.sendAlert(msg) }
                        saveState()
                    }
                }
            }
            "EXITED" -> {
                if (ltp >= trade.entryPrice) {
                    val msg = "[RE-ENTRY TRIGGERED] $symbol re-opened at Buy Price: ${trade.entryPrice} | LTP: $ltp"
                    println(msg)
                    scope.launch { TelegramNotifier.sendAlert(msg) }

                    trade.status = "ACTIVE"
                    trade.stopLoss = gann.stopLoss
                    trade.currentTpLevel = 0
                    trade.entryTime = System.currentTimeMillis()
                    saveState()
                }
            }
        }
    }

    private fun checkAndSendEodSummary() {
        val allExited = tradesMap.values.all { it.status == "EXITED" }
        if (allExited) {
            val summary = buildString {
                append("📊 *END OF DAY P&L SUMMARY REPORT*\n\n")
                var totalPnl = 0.0
                for ((sym, tr) in tradesMap) {
                    val pnl = (tr.stopLoss - tr.entryPrice) * tr.quantity
                    totalPnl += pnl
                    append("• $sym: Entry ${tr.entryPrice} | Qty ${tr.quantity} | Final SL/Exit ${tr.stopLoss} | P&L: ${String.format("%.2f", pnl)}\n")
                }
                append("\n💰 *TOTAL P&L: ${String.format("%.2f", totalPnl)}*")
            }
            println(summary)
            scope.launch { TelegramNotifier.sendAlert(summary) }
        }
    }

    private fun loadState() {
        try {
            if (stateFile.exists()) {
                val jsonStr = stateFile.readText()
                val json = JSONObject(jsonStr)
                for (key in json.keys()) {
                    val obj = json.getJSONObject(key)
                    val trade = TradeState(
                        symbol = obj.getString("symbol"),
                        status = obj.getString("status"),
                        entryPrice = obj.getDouble("entryPrice"),
                        stopLoss = obj.getDouble("stopLoss"),
                        currentTpLevel = obj.getInt("currentTpLevel"),
                        quantity = obj.getInt("quantity"),
                        entryTime = obj.getLong("entryTime")
                    )
                    tradesMap[trade.symbol] = trade
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveState() {
        try {
            val json = JSONObject()
            for ((symbol, trade) in tradesMap) {
                val obj = JSONObject().apply {
                    put("symbol", trade.symbol)
                    put("status", trade.status)
                    put("entryPrice", trade.entryPrice)
                    put("stopLoss", trade.stopLoss)
                    put("currentTpLevel", trade.currentTpLevel)
                    put("quantity", trade.quantity)
                    put("entryTime", trade.entryTime)
                }
                json.put(symbol, obj)
            }
            stateFile.writeText(json.toString(4))
        } catch (_: Exception) {}
    }

    fun printStatus() {
        println("\n=== PAPER TRADING ENGINE STATUS ===")
        for ((symbol, trade) in tradesMap) {
            println("Symbol: $symbol | Status: ${trade.status} | Entry: ${trade.entryPrice} | SL: ${trade.stopLoss} | Active TP Level: ${trade.currentTpLevel}/10 | Qty: ${trade.quantity}")
        }
        println("=====================================")
    }
}
