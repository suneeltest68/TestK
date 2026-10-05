package com.example.viewmodel

import com.example.model.AuthState
import com.example.repository.FyersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuthViewModel(private val repository: FyersRepository = FyersRepository()) {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun prepareLoginUrl(appId: String, redirectUri: String) {
        val url = repository.getLoginUrl(appId, redirectUri)
        _authState.update { it.copy(loginUrl = url, errorMessage = null) }
    }

    suspend fun authenticateWithAuthCode(appId: String, secretKey: String, authCode: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        
        val result = withContext(Dispatchers.IO) {
            repository.exchangeAuthCodeForToken(appId, secretKey, authCode)
        }

        result.fold(
            onSuccess = { token ->
                _authState.update { it.copy(accessToken = token, isLoading = false, errorMessage = null) }
            },
            onFailure = { error ->
                _authState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unknown error") }
            }
        )
    }

    suspend fun fetchHistoricalData(appId: String, accessToken: String, symbol: String, resolution: String, rangeFrom: String, rangeTo: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val result = repository.getHistoricalData(appId, accessToken, symbol, resolution, rangeFrom, rangeTo)
            if (result.isFailure) {
                println("Historical Data Error for $symbol: ${result.exceptionOrNull()?.message}")
            }
            result.getOrNull()
        }
    }

    suspend fun fetchOptionChain(appId: String, accessToken: String, symbol: String, strikeCount: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val result = repository.getOptionChain(appId, accessToken, symbol, strikeCount)
            if (result.isFailure) {
                println("Option Chain Error for $symbol: ${result.exceptionOrNull()?.message}")
            }
            result.getOrNull()
        }
    }

    suspend fun fetchHistoryExpiryDates(appId: String, accessToken: String, symbol: String, rangeFrom: String, rangeTo: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val result = repository.getHistoryExpiryDates(appId, accessToken, symbol, rangeFrom, rangeTo)
            if (result.isFailure) {
                println("Expiry Dates Error: ${result.exceptionOrNull()?.message}")
            }
            result.getOrNull()
        }
    }

    suspend fun fetchHistoryUnderlyingSymbols(appId: String, accessToken: String, symbol: String, expiryDate: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val result = repository.getHistoryUnderlyingSymbols(appId, accessToken, symbol, expiryDate)
            if (result.isFailure) {
                println("Underlying Symbols Error: ${result.exceptionOrNull()?.message}")
            }
            result.getOrNull()
        }
    }

    suspend fun fetchHistoryFNOExpired(appId: String, accessToken: String, fnoSymbol: String, rangeFrom: String, rangeTo: String, resolution: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val result = repository.getHistoryFNOExpired(appId, accessToken, fnoSymbol, rangeFrom, rangeTo, resolution)
            if (result.isFailure) {
                println("FNO Expired History Error for $fnoSymbol: ${result.exceptionOrNull()?.message}")
            }
            result.getOrNull()
        }
    }

    fun getCachedToken(): String? {
        return repository.getCachedToken()
    }

    fun clearToken() {
        repository.clearToken()
    }
}
