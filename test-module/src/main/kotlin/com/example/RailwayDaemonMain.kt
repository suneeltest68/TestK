package com.example

import com.example.viewmodel.AuthViewModel
import com.example.service.TradingWorkflowService
import com.example.service.TelegramNotifier
import com.sun.net.httpserver.HttpServer
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.Duration
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun main() = runBlocking {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        val stackTrace = throwable.stackTraceToString().take(1500)
        val errorMessage = "🚨 [FATAL CRASH] Thread '${thread.name}' crashed:\n${throwable.message}\n\n$stackTrace"
        println(errorMessage)
        try {
            runBlocking {
                TelegramNotifier.sendAlert(errorMessage)
            }
        } catch (_: Exception) {}
    }

    val dotenv = dotenv { ignoreIfMissing = true }
    val viewModel = AuthViewModel()
    val workflowService = TradingWorkflowService(viewModel)
    val istZone = ZoneId.of("Asia/Kolkata")

    println("==================================================")
    println("🚀 FYERS ALGO TRADING BOT - RAILWAY DAEMON (24/7 WEB & CALLBACK)")
    println("==================================================")

    val appId = System.getenv("FYERS_APP_ID") ?: dotenv["FYERS_APP_ID"]
    val secretKey = System.getenv("FYERS_SECRET_KEY") ?: dotenv["FYERS_SECRET_KEY"]
    val redirectUri = System.getenv("FYERS_REDIRECT_URI") ?: dotenv["FYERS_REDIRECT_URI"]
    val portStr = System.getenv("PORT") ?: "8080"
    val port = portStr.toIntOrNull() ?: 8080

    if (appId.isNullOrEmpty() || secretKey.isNullOrEmpty() || redirectUri.isNullOrEmpty()) {
        println("❌ Error: Missing required Fyers environment variables.")
        return@runBlocking
    }

    var authCodeDeferred: CompletableDeferred<String>? = null

    // Start 24/7 Background HTTP Server for Website Dashboard, API, and OAuth Callback
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext("/") { exchange ->
        val path = exchange.requestURI.path
        try {
            if (path == "/api/journal" || path == "/journal") {
                val file = File("trading_journal.json")
                val jsonStr = if (file.exists()) file.readText() else "{\"trades\":[],\"stats\":{}}"
                exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                val bytes = jsonStr.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else if (path == "/callback") {
                val query = exchange.requestURI.query ?: ""
                val queryParams = query.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) {
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()) to 
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                    } else {
                        "" to ""
                    }
                }
                val code = queryParams["s_code"] ?: queryParams["auth_code"] ?: queryParams["code"]
                val responseHtml = if (!code.isNullOrEmpty()) {
                    "<html><body style='font-family: Arial; text-align: center; margin-top: 50px;'><h1 style='color: green;'>✅ Fyers Authentication Successful!</h1><p>Authorization code received. You can now close this tab and return to Railway.</p></body></html>"
                } else {
                    "<html><body style='font-family: Arial; text-align: center; margin-top: 50px;'><h1 style='color: red;'>❌ Authentication Failed</h1><p>No authorization code found in callback query.</p></body></html>"
                }
                exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                val bytes = responseHtml.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }

                if (!code.isNullOrEmpty()) {
                    authCodeDeferred?.complete(code)
                }
            } else {
                // Serve Gann Trading Journal Dashboard (journal-web/index.html)
                val file = File("journal-web/index.html")
                val htmlStr = if (file.exists()) file.readText() else "<h1>Gann Trading Journal</h1><p>index.html not found.</p>"
                exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                val bytes = htmlStr.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        } catch (e: Exception) {
            val err = "Error: ${e.message}".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(500, err.size.toLong())
            exchange.responseBody.use { it.write(err) }
        }
    }
    server.setExecutor(null)
    server.start()
    println("[HTTP Server] Running 24/7 on port $port (Dashboard: /, API: /api/journal, OAuth: /callback)")

    var isFirstRun = true

    while (true) {
        if (!isFirstRun) {
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
        } else {
            println("\n[Daemon] First startup: Running trading session immediately...")
            isFirstRun = false
        }

        println("\n--------------------------------------------------")
        println("🌅 [Daily Routine] Starting Trading Session...")
        println("--------------------------------------------------")

        // Clear cached token at the start of every daily trading session
        viewModel.clearToken()

        val cachedToken = viewModel.getCachedToken()
        var activeToken: String? = null

        if (cachedToken != null) {
            println("[Cache] Using cached Access Token.")
            activeToken = cachedToken
        }

        if (activeToken == null) {
            println("[Auth] Access token expired or missing. Waiting for OAuth callback...")
            
            viewModel.prepareLoginUrl(appId, redirectUri)
            val loginUrl = viewModel.authState.value.loginUrl
            
            TelegramNotifier.sendAlert("⚠️ [Fyers Railway Daemon] Access Token expired or missing! Please login & authorize:\n$loginUrl")

            authCodeDeferred = CompletableDeferred()

            // Wait until callback receives auth code (or timeout after 1 hour)
            val authCode = try {
                withTimeout(60 * 60 * 1000L) {
                    authCodeDeferred?.await()
                }
            } catch (_: Exception) {
                null
            }

            authCodeDeferred = null

            if (authCode != null) {
                println("[Auth] Exchanging received auth code for access token...")
                viewModel.authenticateWithAuthCode(appId, secretKey, authCode)
                val finalState = viewModel.authState.value
                if (finalState.accessToken != null) {
                    activeToken = finalState.accessToken
                    TelegramNotifier.sendAlert("✅ [Fyers Railway Daemon] Access Token refreshed & cached successfully via web callback!")
                } else {
                    TelegramNotifier.sendAlert("❌ [Fyers Railway Daemon] Token exchange failed: ${finalState.errorMessage}")
                }
            } else {
                TelegramNotifier.sendAlert("❌ [Fyers Railway Daemon] Authentication timed out waiting for web callback.")
                delay(10 * 60 * 1000L)
                continue
            }
        }

        if (activeToken != null) {
            try {
                workflowService.executeCurrentExpiryWorkflow(appId, activeToken)
            } catch (e: Exception) {
                println("[Daemon Error] Workflow exception: ${e.message}")
                TelegramNotifier.sendAlert("❌ [Fyers Daemon] Error during execution: ${e.message}")
            }
        }

        // Sleep 10 minutes before looping to ensure we don't double-trigger on the same day
        delay(10 * 60 * 1000L)
    }
}
