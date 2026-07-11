package com.nethal.core.scheduling.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * `SQLiteOpenHelper` puro (issue #112) — decisão registrada no PR: Room + KSP não entraram. Duas
 * consultas triviais (inserir, listar N mais recentes por origem) não justificam somar um
 * toolchain de annotation processing novo ao projeto (nenhum outro módulo usa KSP/kapt hoje).
 * `SqliteMeasurementSampleRepository` é a única classe que conhece este esquema — trocar para Room
 * mais tarde (se #104 precisar de queries mais ricas) é uma implementação nova de
 * `MeasurementSampleRepository`, sem tocar no contrato que o resto do módulo consome.
 */
internal class MeasurementDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SOURCE TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_SUCCESS INTEGER NOT NULL,
                $COL_DOWNLOAD_MBPS REAL,
                $COL_UPLOAD_MBPS REAL,
                $COL_LATENCY_MS REAL,
                $COL_JITTER_MS REAL,
                $COL_PACKET_LOSS_PERCENT REAL,
                $COL_BUFFERBLOAT_MS REAL,
                $COL_ERROR_MESSAGE TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_${TABLE_NAME}_source_timestamp ON $TABLE_NAME ($COL_SOURCE, $COL_TIMESTAMP DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    companion object {
        const val DATABASE_NAME = "nethal_measurement.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NAME = "measurement_sample"
        const val COL_ID = "id"
        const val COL_SOURCE = "source"
        const val COL_TIMESTAMP = "timestamp_epoch_ms"
        const val COL_SUCCESS = "success"
        const val COL_DOWNLOAD_MBPS = "download_mbps"
        const val COL_UPLOAD_MBPS = "upload_mbps"
        const val COL_LATENCY_MS = "latency_ms"
        const val COL_JITTER_MS = "jitter_ms"
        const val COL_PACKET_LOSS_PERCENT = "packet_loss_percent"
        const val COL_BUFFERBLOAT_MS = "bufferbloat_ms"
        const val COL_ERROR_MESSAGE = "error_message"
    }
}
