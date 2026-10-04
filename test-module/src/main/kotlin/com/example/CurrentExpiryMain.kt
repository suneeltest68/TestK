package com.example

import com.example.viewmodel.AuthViewModel
import com.example.service.TradingWorkflowService
import kotlinx.coroutines.runBlocking

fun main() {
    val viewModel = AuthViewModel()
    val workflowService = TradingWorkflowService(viewModel)

    println("=== Fyers API v3 Current Expiry ATM Options Flow (MVVM) ===")
    val appId = "QCLMTKB73R-100"

    // Check if we have a valid cached token
    val cachedToken = viewModel.getCachedToken()
    if (cachedToken != null) {
        println("\n[Cache] Found valid cached Access Token! Skipping login flow.")
        runBlocking {
            workflowService.executeCurrentExpiryWorkflow(appId, cachedToken)
        }
        return
    }

    println("No cached token found. Please run Main.kt first to generate and cache your access token.")
}
