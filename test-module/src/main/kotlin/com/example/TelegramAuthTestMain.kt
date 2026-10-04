package com.example

import com.example.viewmodel.AuthViewModel
import com.example.service.TradingWorkflowService
import com.example.service.TelegramNotifier
import com.example.util.AuthUtils
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Scanner

fun main() {
    val dotenv = dotenv {
        ignoreIfMissing = true
    }
    val viewModel = AuthViewModel()
    val workflowService = TradingWorkflowService(viewModel)
    val scanner = Scanner(System.`in`)

    println("==================================================")
    println("🧪 TELEGRAM AUTHENTICATION & WORKFLOW TEST MAIN")
    println("==================================================")

    val appId = System.getenv("FYERS_APP_ID") ?: dotenv["FYERS_APP_ID"]
    val secretKey = System.getenv("FYERS_SECRET_KEY") ?: dotenv["FYERS_SECRET_KEY"]
    val redirectUri = System.getenv("FYERS_REDIRECT_URI") ?: dotenv["FYERS_REDIRECT_URI"]

    if (appId.isNullOrEmpty() || secretKey.isNullOrEmpty() || redirectUri.isNullOrEmpty()) {
        println("❌ Error: Missing Fyers environment variables (FYERS_APP_ID, FYERS_SECRET_KEY, FYERS_REDIRECT_URI).")
        return
    }

    // Force test behavior: Delete cached token file to simulate expired/missing token
    val tokenFile = File("access_token.txt")
    if (tokenFile.exists()) {
        tokenFile.delete()
        println("[Test Setup] Deleted cached 'access_token.txt' to force re-authentication flow.")
    }

    println("\n[1] Preparing Fyers Login URL...")
    viewModel.prepareLoginUrl(appId, redirectUri)
    val loginUrl = viewModel.authState.value.loginUrl

    if (loginUrl.isEmpty()) {
        println("❌ Failed to generate login URL.")
        return
    }

    println("Generated Login URL: $loginUrl")

    // Send Telegram Notification requesting authorization
    println("\n[2] Sending Authorization Request to Telegram...")
    runBlocking {
        TelegramNotifier.sendAlert("🧪 [Test Bot] Authorization required! Please login & authorize via Fyers:\n$loginUrl")
    }
    println("✅ Telegram alert sent successfully!")

    // Prompt user for auth code / redirect URL from console to continue the process
    println("\n[3] Waiting for manual authorization...")
    print("Paste either the full redirect URL or the 'auth_code' here: ")
    var input = scanner.nextLine().trim()
    while (input.isEmpty()) {
        print("Input cannot be empty. Please paste the redirect URL or auth_code: ")
        input = scanner.nextLine().trim()
    }

    val authCode = AuthUtils.extractAuthCode(input)
    println("Extracted Auth Code: $authCode")

    // Exchange auth code for access token
    println("\n[4] Exchanging auth code for access token...")
    runBlocking {
        viewModel.authenticateWithAuthCode(appId, secretKey, authCode)
    }

    val finalState = viewModel.authState.value
    if (finalState.accessToken != null) {
        println("\n🎉 SUCCESS! Access Token acquired and cached.")
        runBlocking {
            TelegramNotifier.sendAlert("✅ [Test Bot] Authentication successful! Continuing trading workflow...")
            
            // Continue the process: execute trading workflow
            workflowService.executeCurrentExpiryWorkflow(appId, finalState.accessToken)
        }
    } else {
        val errorMsg = finalState.errorMessage ?: "Unknown error"
        println("\n❌ FAILED: $errorMsg")
        runBlocking {
            TelegramNotifier.sendAlert("❌ [Test Bot] Authentication failed: $errorMsg")
        }
    }
}
