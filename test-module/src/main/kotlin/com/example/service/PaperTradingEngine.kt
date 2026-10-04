package com.example.service

import com.example.model.GannLevels
import com.example.model.TradeState
import org.json.JSONObject
import java.io.File
import java.time.LocalTime
import java.time.ZoneId

class PaperTradingEngine(
    private val symbolsGannMap: Map<String, GannLevels>,
    private val quantitiesMap: Map<String, Int>
) {
    private val stateFile = File("active_trades.json")
    private val tradesMap = mutableMapOf<String, TradeState>()

    init {
        loadState()
        for ((symbol, gann) in symbolsGannMap) {
            if (!tradesMap.containsKey(symbol)) {
                val qty = quantitiesMap[symbol] ?: 1
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
                println("[AUTO SQUARE-OFF] 3:25 PM EOD reached! Force exiting $symbol at LTP $ltp")
                trade.status = "EXITED"
                saveState()
            }
            return
        }

        when (trade.status) {
            "IDLE" -> {
                if (ltp >= trade.entryPrice) {
                    println("[PAPER TRADE] [$symbol] LTP ($ltp) >= Buy Price (${trade.entryPrice}) -> Placing LIMIT BUY ORDER for ${trade.quantity} qty")
                    trade.status = "ACTIVE"
                    trade.currentTpLevel = 0
                    trade.entryTime = System.currentTimeMillis()
                    saveState()
                } else {
                    println("[PAPER TRADE] [$symbol] LTP ($ltp) < Buy Price (${trade.entryPrice}) -> Placing STOP BUY ORDER (Buy Stop) for ${trade.quantity} qty")
                    trade.status = "ACTIVE"
                    trade.currentTpLevel = 0
                    trade.entryTime = System.currentTimeMillis()
                    saveState()
                }
            }
            "ACTIVE" -> {
                // 1. Check Stop Loss
                if (ltp <= trade.stopLoss) {
                    println("[PAPER TRADE] [$symbol] STOP LOSS HIT! LTP ($ltp) <= SL (${trade.stopLoss}) -> EXITING TRADE.")
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
                        println("[PAPER TRADE] [$symbol] TARGET ${trade.currentTpLevel} HIT! LTP ($ltp) >= TP${trade.currentTpLevel} ($nextTarget)")

                        // Trailing SL mechanism:
                        // TP1 hit -> Move SL to Buy Price (Entry)
                        // TP2 hit -> Move SL to TP1, etc.
                        if (trade.currentTpLevel == 1) {
                            trade.stopLoss = trade.entryPrice
                            println("[PAPER TRADE] [$symbol] Trailing SL moved to Entry Price: ${trade.stopLoss}")
                        } else {
                            trade.stopLoss = targets[trade.currentTpLevel - 2]
                            println("[PAPER TRADE] [$symbol] Trailing SL moved to TP${trade.currentTpLevel - 1}: ${trade.stopLoss}")
                        }
                        saveState()
                    }
                }
            }
            "EXITED" -> {
                // Re-entry logic: If LTP comes again to buyPrice and no active trade is running
                if (ltp >= trade.entryPrice) {
                    println("[PAPER TRADE] [$symbol] RE-ENTRY TRIGGERED! LTP ($ltp) returned to Buy Price (${trade.entryPrice}) -> Re-opening trade.")
                    trade.status = "ACTIVE"
                    trade.stopLoss = gann.stopLoss
                    trade.currentTpLevel = 0
                    trade.entryTime = System.currentTimeMillis()
                    saveState()
                }
            }
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
