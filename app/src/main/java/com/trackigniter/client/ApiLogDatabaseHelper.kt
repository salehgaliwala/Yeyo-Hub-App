package com.trackigniter.client

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class ApiLogDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE api_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "timestamp TEXT," +
                    "method TEXT," +
                    "url TEXT," +
                    "request_body TEXT," +
                    "response_code INTEGER," +
                    "response_body TEXT," +
                    "error TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS api_logs")
        onCreate(db)
    }

    fun logApiCall(method: String, url: String, requestBody: String?, responseCode: Int, responseBody: String?, error: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("method", method)
            put("url", url)
            put("request_body", requestBody)
            put("response_code", responseCode)
            put("response_body", responseBody)
            put("error", error)
        }
        db.insert("api_logs", null, values)
        
        // Circular buffer: keep only last 1000 logs to prevent DB bloat
        db.execSQL("DELETE FROM api_logs WHERE id IN (SELECT id FROM api_logs ORDER BY id DESC LIMIT -1 OFFSET 1000)")
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "api_logs.db"
    }
}
