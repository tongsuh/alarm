package com.example.flashalarm.sleep.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.flashalarm.sleep.model.SleepEpoch
import com.example.flashalarm.sleep.model.SleepSession
import com.example.flashalarm.sleep.model.SleepStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SleepRecordRepository(context: Context) {

    private val dbHelper = SleepDatabaseHelper(context.applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _sessionsFlow = MutableStateFlow<List<SleepSession>>(emptyList())
    val sessionsFlow: StateFlow<List<SleepSession>> = _sessionsFlow.asStateFlow()

    init {
        refreshSessions()
    }

    fun refreshSessions() {
        scope.launch {
            val list = loadAllSessionsInternal()
            _sessionsFlow.value = list
        }
    }

    suspend fun saveSession(session: SleepSession) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(SleepDatabaseHelper.COL_SESSION_ID, session.id)
                put(SleepDatabaseHelper.COL_DATE_STRING, session.dateString)
                put(SleepDatabaseHelper.COL_START_TIME, session.startTimestamp)
                put(SleepDatabaseHelper.COL_END_TIME, session.endTimestamp)
                put(SleepDatabaseHelper.COL_SLEEP_ONSET_TIME, session.sleepOnsetTimestamp)
                put(SleepDatabaseHelper.COL_TOTAL_DURATION_MIN, session.totalDurationMin)
                put(SleepDatabaseHelper.COL_NET_SLEEP_MIN, session.netSleepDurationMin)
                put(SleepDatabaseHelper.COL_DEEP_SLEEP_MIN, session.deepSleepMin)
                put(SleepDatabaseHelper.COL_LIGHT_SLEEP_MIN, session.lightSleepMin)
                put(SleepDatabaseHelper.COL_REM_SLEEP_MIN, session.remSleepMin)
                put(SleepDatabaseHelper.COL_AWAKE_MIN, session.awakeMin)
                put(SleepDatabaseHelper.COL_CUE_TRIGGER_COUNT, session.cueTriggerCount)
                put(SleepDatabaseHelper.COL_SLEEP_SCORE, session.sleepScore)
                put(SleepDatabaseHelper.COL_AVG_BREATH_BPM, session.avgBreathBpm)
                put(SleepDatabaseHelper.COL_TURNOVER_COUNT, session.turnoverCount)
            }
            db.insertWithOnConflict(
                SleepDatabaseHelper.TABLE_SESSIONS,
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )

            // 如果 session 自带 epochs，批量插入
            if (session.epochs.isNotEmpty()) {
                insertEpochsInternal(db, session.epochs)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshSessions()
    }

    suspend fun insertEpochs(epochs: List<SleepEpoch>) = withContext(Dispatchers.IO) {
        if (epochs.isEmpty()) return@withContext
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            insertEpochsInternal(db, epochs)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertEpochsInternal(db: android.database.sqlite.SQLiteDatabase, epochs: List<SleepEpoch>) {
        val stmt = db.compileStatement(
            """
            INSERT INTO ${SleepDatabaseHelper.TABLE_EPOCHS} (
                ${SleepDatabaseHelper.COL_EPOCH_SESSION_ID},
                ${SleepDatabaseHelper.COL_EPOCH_TIMESTAMP},
                ${SleepDatabaseHelper.COL_EPOCH_STAGE},
                ${SleepDatabaseHelper.COL_EPOCH_BREATH_BPM},
                ${SleepDatabaseHelper.COL_EPOCH_RESPIRATORY_CV},
                ${SleepDatabaseHelper.COL_EPOCH_MOVEMENT_SCORE},
                ${SleepDatabaseHelper.COL_EPOCH_IS_CUE}
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )
        for (epoch in epochs) {
            stmt.bindLong(1, epoch.sessionId)
            stmt.bindLong(2, epoch.timestamp)
            stmt.bindLong(3, epoch.stage.code.toLong())
            stmt.bindDouble(4, epoch.breathBpm.toDouble())
            stmt.bindDouble(5, epoch.respiratoryCv.toDouble())
            stmt.bindDouble(6, epoch.movementScore.toDouble())
            stmt.bindLong(7, if (epoch.isCueTriggered) 1L else 0L)
            stmt.executeInsert()
            stmt.clearBindings()
        }
    }

    suspend fun getLatestSessionWithEpochs(): SleepSession? = withContext(Dispatchers.IO) {
        val sessions = loadAllSessionsInternal()
        val latest = sessions.firstOrNull() ?: return@withContext null
        val epochs = loadEpochsForSessionInternal(latest.id)
        latest.copy(epochs = epochs)
    }

    suspend fun getSessionByDateWithEpochs(dateString: String): SleepSession? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        var session: SleepSession? = null
        val cursor = db.query(
            SleepDatabaseHelper.TABLE_SESSIONS,
            null,
            "${SleepDatabaseHelper.COL_DATE_STRING} = ?",
            arrayOf(dateString),
            null,
            null,
            "${SleepDatabaseHelper.COL_START_TIME} DESC",
            "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                session = parseSessionFromCursor(it)
            }
        }
        return@withContext session?.let {
            val epochs = loadEpochsForSessionInternal(it.id)
            it.copy(epochs = epochs)
        }
    }

    private fun loadAllSessionsInternal(): List<SleepSession> {
        val list = mutableListOf<SleepSession>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SleepDatabaseHelper.TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "${SleepDatabaseHelper.COL_START_TIME} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseSessionFromCursor(it))
            }
        }
        return list
    }

    private fun loadEpochsForSessionInternal(sessionId: Long): List<SleepEpoch> {
        val list = mutableListOf<SleepEpoch>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SleepDatabaseHelper.TABLE_EPOCHS,
            null,
            "${SleepDatabaseHelper.COL_EPOCH_SESSION_ID} = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            "${SleepDatabaseHelper.COL_EPOCH_TIMESTAMP} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SleepEpoch(
                        id = it.getLong(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_ID)),
                        sessionId = it.getLong(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_SESSION_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_TIMESTAMP)),
                        stage = SleepStage.fromCode(it.getInt(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_STAGE))),
                        breathBpm = it.getFloat(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_BREATH_BPM)),
                        respiratoryCv = it.getFloat(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_RESPIRATORY_CV)),
                        movementScore = it.getFloat(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_MOVEMENT_SCORE)),
                        isCueTriggered = it.getInt(it.getColumnIndexOrThrow(SleepDatabaseHelper.COL_EPOCH_IS_CUE)) == 1
                    )
                )
            }
        }
        return list
    }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(SleepDatabaseHelper.TABLE_EPOCHS, "${SleepDatabaseHelper.COL_EPOCH_SESSION_ID} = ?", arrayOf(sessionId.toString()))
        db.delete(SleepDatabaseHelper.TABLE_SESSIONS, "${SleepDatabaseHelper.COL_SESSION_ID} = ?", arrayOf(sessionId.toString()))
        refreshSessions()
    }

    private fun parseSessionFromCursor(cursor: Cursor): SleepSession {
        return SleepSession(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_SESSION_ID)),
            dateString = cursor.getString(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_DATE_STRING)),
            startTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_START_TIME)),
            endTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_END_TIME)),
            sleepOnsetTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_SLEEP_ONSET_TIME)),
            totalDurationMin = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_TOTAL_DURATION_MIN)),
            netSleepDurationMin = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_NET_SLEEP_MIN)),
            deepSleepMin = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_DEEP_SLEEP_MIN)),
            lightSleepMin = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_LIGHT_SLEEP_MIN)),
            remSleepMin = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_REM_SLEEP_MIN)),
            awakeMin = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_AWAKE_MIN)),
            cueTriggerCount = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_CUE_TRIGGER_COUNT)),
            sleepScore = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_SLEEP_SCORE)),
            avgBreathBpm = cursor.getFloat(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_AVG_BREATH_BPM)),
            turnoverCount = cursor.getInt(cursor.getColumnIndexOrThrow(SleepDatabaseHelper.COL_TURNOVER_COUNT))
        )
    }
}
