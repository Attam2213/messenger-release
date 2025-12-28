package com.example.messenger.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import com.example.messenger.shared.db.AppDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val appDataDir = File(System.getProperty("user.home"), "AppData/Local/Messenger")
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
        }
        val dbFile = File(appDataDir, "messenger.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        
        if (!dbFile.exists()) {
             AppDatabase.Schema.create(driver)
        }
        return driver
    }
}
