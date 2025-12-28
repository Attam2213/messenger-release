package com.example.messenger.shared.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver

import com.example.messenger.shared.db.AppDatabase

object DatabaseHelper {
    fun createDatabase(driver: SqlDriver): AppDatabase {
        return AppDatabase(
            driver = driver
        )
    }
}
