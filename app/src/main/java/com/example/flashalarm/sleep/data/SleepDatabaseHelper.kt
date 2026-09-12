package com.example.flashalarm.sleep.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SleepDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "flash_alarm_sleep.db"
        const val DATABASE_VERSION = 1

        const val TABLE_SESSIONS = "sleep_sessions"
        const val COL_SESSION_ID = "id"
        const val COL_DATE_STRING = "date_string"
        const val COL_START_TIME = "start_timestamp"
        const val COL_END_TIME = "end_timestamp"
        const val COL_SLEEP_ONSET_TIME = "sleep_onset_timestamp"
        const val COL_TOTAL_DURATION_MIN = "total_duration_min"
        const val COL_NET_SLEEP_MIN = "net_sleep_duration_min"
        const val COL_DEEP_SLEEP_MIN = "deep_sleep_min"
        const val COL_LIGHT_SLEEP_MIN = "light_sleep_min"
        const val COL_REM_SLEEP_MIN = "rem_sleep_min"
        const val COL_AWAKE_MIN = "awake_min"
        const val COL_CUE_TRIGGER_COUNT = "cue_trigger_count"
        const val COL_SLEEP_SCORE = "sleep_score"
        const val COL_AVG_BREATH_BPM = "avg_breath_bpm"
        const val COL_TURNOVER_COUNT = "turnover_count"

        const val TABLE_EPOCHS = "sleep_epochs"
        const val COL_EPOCH_ID = "id"
        const val COL_EPOCH_SESSION_ID = "session_id"
        const val COL_EPOCH_TIMESTAMP = "timestamp"
        const val COL_EPOCH_STAGE = "stage"
        const val COL_EPOCH_BREATH_BPM = "breath_bpm"
        const val COL_EPOCH_RESPIRATORY_CV = "respiratory_cv"
        const val COL_EPOCH_MOVEMENT_SCORE = "movement_score"
        const val COL_EPOCH_IS_CUE = "is_cue_triggered"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                $COL_SESSION_ID INTEGER PRIMARY KEY,
                $COL_DATE_STRING TEXT NOT NULL,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_END_TIME INTEGER NOT NULL,
                $COL_SLEEP_ONSET_TIME INTEGER NOT NULL,
                $COL_TOTAL_DURATION_MIN INTEGER NOT NULL,
                $COL_NET_SLEEP_MIN INTEGER NOT NULL,
                $COL_DEEP_SLEEP_MIN INTEGER NOT NULL,
                $COL_LIGHT_SLEEP_MIN INTEGER NOT NULL,
                $COL_REM_SLEEP_MIN INTEGER NOT NULL,
                $COL_AWAKE_MIN INTEGER NOT NULL,
                $COL_CUE_TRIGGER_COUNT INTEGER NOT NULL,
                $COL_SLEEP_SCORE INTEGER NOT NULL,
                $COL_AVG_BREATH_BPM REAL NOT NULL,
                $COL_TURNOVER_COUNT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_EPOCHS (
                $COL_EPOCH_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EPOCH_SESSION_ID INTEGER NOT NULL,
                $COL_EPOCH_TIMESTAMP INTEGER NOT NULL,
                $COL_EPOCH_STAGE INTEGER NOT NULL,
                $COL_EPOCH_BREATH_BPM REAL NOT NULL,
                $COL_EPOCH_RESPIRATORY_CV REAL NOT NULL,
                $COL_EPOCH_MOVEMENT_SCORE REAL NOT NULL,
                $COL_EPOCH_IS_CUE INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_date ON $TABLE_SESSIONS($COL_DATE_STRING)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_epochs_session ON $TABLE_EPOCHS($COL_EPOCH_SESSION_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 版本升级策略
    }
}
