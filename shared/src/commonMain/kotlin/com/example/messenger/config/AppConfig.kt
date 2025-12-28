package com.example.messenger.config

object AppConfig {
    // TODO: In a real app, this might come from build config or environment variables
    const val BASE_URL = "155.212.170.166"
    const val WS_PORT = 8000
    const val HTTP_PORT = 8000
    
    val WS_URL: String
        get() = "$BASE_URL:$WS_PORT"
        
    val API_URL: String
        get() = "http://$BASE_URL:$HTTP_PORT"
}
