package com.example

import com.example.viewmodel.AuthViewModel
import kotlinx.coroutines.runBlocking
import java.util.Scanner
import java.net.URI
import java.net.URLDecoder

fun main() {
    val viewModel = AuthViewModel()
    val scanner = Scanner(System.`in`)

    println("=== Fyers API v3 Authentication, Profile, Option Chain & History Flow (MVVM) ===")
    val appId = "QCLMTKB73R-100"
    val secretKey = "RWLN4NNE8N"
    val redirectUri = "https://redirect-service-algo.onrender.com/"

    // Check if we have a valid cached token
    val cachedToken = viewModel.getCachedToken()
    if (cachedToken != null) {
        println("\n[Cache] Found valid cached Access Token! Skipping login flow.")
        println("Access Token: $cachedToken")

        val success = runBlocking {
            fetchUserDataAndHistory(viewModel, appId, cachedToken)
        }
        if (success) {
            return
        } else {
            println("\nCached token is invalid or expired. Re-initiating login flow...")
        }
    }

    performLoginFlow(viewModel, scanner, appId, secretKey, redirectUri)
}

fun performLoginFlow(viewModel: AuthViewModel, scanner: Scanner, appId: String, secretKey: String, redirectUri: String) {
    // Step 1: Generate Login URL only when login is needed
    viewModel.prepareLoginUrl(appId, redirectUri)

    print("Paste either the full redirect URL or the 'auth_code': ")
    var input = scanner.nextLine().trim()
    while (input.isEmpty()) {
        print("Input cannot be empty. Please paste the redirect URL or auth_code: ")
        input = scanner.nextLine().trim()
    }

    val authCode = extractAuthCode(input)

    // Step 2: Exchange Auth Code for Access Token
    println("\n[2] Exchanging auth code for access token...")
    runBlocking {
        viewModel.authenticateWithAuthCode(appId, secretKey, authCode)
    }

    val finalState = viewModel.authState.value
    if (finalState.accessToken != null) {
        println("\nSUCCESS! Access Token acquired and cached:")
        println(finalState.accessToken)

        runBlocking {
            fetchUserDataAndHistory(viewModel, appId, finalState.accessToken)
        }
    } else {
        println("\nFAILED: ${finalState.errorMessage}")
    }
}

suspend fun fetchUserDataAndHistory(viewModel: AuthViewModel, appId: String, token: String): Boolean {
    // Step 3: Fetch Profile Info
    println("\n[3] Fetching User Profile...")
    val profile = viewModel.fetchProfile(appId, token)
    if (profile == null) {
        println("\nFailed to fetch profile info.")
        return false
    }

    println("\nUSER PROFILE INFO:")
    println(profile.toString(4))

    // Step 4: Fetch Nifty 50 Index Historical Data
    println("\n[4] Fetching Nifty 50 Index Historical Data (30-Sep-2026 to 01-Oct-2026)...")
    val history = viewModel.fetchHistoricalData(
        appId = appId,
        accessToken = token,
        symbol = "NSE:NIFTY50-INDEX",
        resolution = "D",
        rangeFrom = "2026-09-30",
        rangeTo = "2026-10-01"
    )
    if (history != null) {
        println("\nNIFTY 50 INDEX HISTORICAL DATA:")
        println(history.toString(4))
    } else {
        println("\nFailed to fetch Nifty index historical data.")
    }

    // Step 5: Fetch Option Chain to find Nifty 22500 PE Symbol
    println("\n[5] Fetching Nifty Option Chain...")
    val optionChain = viewModel.fetchOptionChain(appId, token, "NSE:NIFTY50-INDEX", 10)
    if (optionChain != null) {
        println("\nNIFTY OPTION CHAIN (Preview):")
        println(optionChain.toString(4).take(1000) + "\n... [truncated]")
    } else {
        println("\nFailed to fetch option chain.")
    }

    // Step 6: Fetch Nifty 22500 PE Historical Data
    // Note: Fyers option symbol format e.g. NSE:NIFTY2693022500PE or derived from option chain
    val peSymbol = "NSE:NIFTY26O0622500PE"
    println("\n[6] Fetching Nifty 22500 PE Historical Data ($peSymbol) (30-Sep-2026 to 01-Oct-2026)...")
    val peHistory = viewModel.fetchHistoricalData(
        appId = appId,
        accessToken = token,
        symbol = peSymbol,
        resolution = "D",
        rangeFrom = "2026-09-30",
        rangeTo = "2026-10-01"
    )
    if (peHistory != null) {
        println("\nNIFTY 22500 PE HISTORICAL DATA:")
        println(peHistory.toString(4))
    } else {
        println("\nFailed to fetch Nifty 22500 PE historical data.")
    }

    return true
}

fun extractAuthCode(input: String): String {
    val trimmed = input.trim()
    if (!trimmed.contains("auth_code=")) {
        return trimmed
    }
    return try {
        val uri = URI(if (trimmed.contains("?")) trimmed else trimmed.replaceFirst("/", "/?"))
        val query = uri.query ?: ""
        for (pair in query.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                if (key == "auth_code") {
                    return URLDecoder.decode(value, "UTF-8")
                }
            }
        }
        trimmed
    } catch (_: Exception) {
        val regex = "auth_code=([^&]+)".toRegex()
        val match = regex.find(trimmed)
        match?.groupValues?.get(1) ?: trimmed
    }
}
