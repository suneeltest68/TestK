package com.example

import com.example.viewmodel.AuthViewModel
import com.example.service.TradingWorkflowService
import com.example.service.TelegramNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.Duration

fun main() = runBlocking {
    val viewModel = AuthViewModel()
    val workflowService = TradingWorkflowService(viewModel)
    val istZone = ZoneId.of("Asia/Kolkata")

    println("==================================================")
    println("🚀 FYERS ALGO TRADING BOT - 24/7 PRODUCTION DAEMON")
    println("==================================================")

    val appId = System.getenv("FYERS_APP_ID")

    while (true) {
        val now = ZonedDateTime.now(istZone)
        val targetTimeToday = now.withHour(9).withMinute(0).withSecond(0).withNano(0)

        val nextRun = if (now.isAfter(targetTimeToday)) {
            targetTimeToday.plusDays(1)
        } else {
            targetTimeToday
        }

        val delayMillis = Duration.between(now, nextRun).toMillis()
        println("\n[Daemon] Next daily startup scheduled for: $nextRun (in ${delayMillis / 1000 / 60} minutes)")
        
        delay(delayMillis)

        println("\n--------------------------------------------------")
        println("🌅 [Daily Routine] Starting 9:00 AM Trading Session...")
        println("--------------------------------------------------")

        val cachedToken = viewModel.getCachedToken()
        var activeToken: String? = null

        if (cachedToken != null) {
            println("[Cache] Using cached Access Token.")
            activeToken = cachedToken
        }

        if (activeToken == null) {
            println("[Auth] Access token expired or missing.")
            viewModel.prepareLoginUrl(appId, System.getenv("FYERS_REDIRECT_URI"))
            val loginUrl = viewModel.authState.value.loginUrl
            TelegramNotifier.sendAlert("⚠️ [Fyers Daemon] Access Token expired or missing! Please login & authorize:\n$loginUrl")
            
            println("[Auth] Waiting 1 hour for manual re-authentication before retrying...")
            delay(60 * 60 * 1000L)
            continue
        }

        try {
            // Runs workflow: waits for 9:15 AM open, trades through the day, auto squares off at 3:25 PM, sends EOD P&L report
            workflowService.executeCurrentExpiryWorkflow(appId, activeToken)
        } catch (e: Exception) {
            println("[Daemon Error] Workflow exception: ${e.message}")
            TelegramNotifier.sendAlert("❌ [Fyers Daemon] Error during execution: ${e.message}")
        }

        // Sleep 10 minutes before looping to ensure we don't double-trigger on the same day
        delay(10 * 60 * 1000L)
    }
}
