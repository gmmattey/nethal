package com.nethal.core.scheduling.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.nethal.core.scheduling.MeasurementSample
import com.nethal.core.scheduling.MeasurementSampleRepository
import com.nethal.core.scheduling.MeasurementSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementação real de [MeasurementSampleRepository] sobre [MeasurementDbHelper] — ver KDoc dele
 * para a decisão de não usar Room. Todo acesso ao banco roda em [Dispatchers.IO].
 */
class SqliteMeasurementSampleRepository(context: Context) : MeasurementSampleRepository {

    private val dbHelper = MeasurementDbHelper(context)

    override suspend fun insert(sample: MeasurementSample) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MeasurementDbHelper.COL_SOURCE, sample.source.name)
            put(MeasurementDbHelper.COL_TIMESTAMP, sample.timestampEpochMs)
            put(MeasurementDbHelper.COL_SUCCESS, if (sample.success) 1 else 0)
            put(MeasurementDbHelper.COL_DOWNLOAD_MBPS, sample.downloadMbps)
            put(MeasurementDbHelper.COL_UPLOAD_MBPS, sample.uploadMbps)
            put(MeasurementDbHelper.COL_LATENCY_MS, sample.latencyMs)
            put(MeasurementDbHelper.COL_JITTER_MS, sample.jitterMs)
            put(MeasurementDbHelper.COL_PACKET_LOSS_PERCENT, sample.packetLossPercent)
            put(MeasurementDbHelper.COL_BUFFERBLOAT_MS, sample.bufferbloatMs)
            put(MeasurementDbHelper.COL_ERROR_MESSAGE, sample.errorMessage)
        }
        dbHelper.writableDatabase.insert(MeasurementDbHelper.TABLE_NAME, null, values)
        Unit
    }

    override suspend fun recent(source: MeasurementSourceType, limit: Int): List<MeasurementSample> =
        withContext(Dispatchers.IO) {
            dbHelper.readableDatabase.query(
                MeasurementDbHelper.TABLE_NAME,
                null,
                "${MeasurementDbHelper.COL_SOURCE} = ?",
                arrayOf(source.name),
                null,
                null,
                "${MeasurementDbHelper.COL_TIMESTAMP} DESC",
                limit.toString(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSample())
                    }
                }
            }
        }

    override suspend fun deleteOlderThan(source: MeasurementSourceType, beforeEpochMs: Long) =
        withContext(Dispatchers.IO) {
            dbHelper.writableDatabase.delete(
                MeasurementDbHelper.TABLE_NAME,
                "${MeasurementDbHelper.COL_SOURCE} = ? AND ${MeasurementDbHelper.COL_TIMESTAMP} < ?",
                arrayOf(source.name, beforeEpochMs.toString()),
            )
            Unit
        }

    private fun Cursor.toSample(): MeasurementSample {
        fun doubleOrNull(columnName: String): Double? {
            val index = getColumnIndexOrThrow(columnName)
            return if (isNull(index)) null else getDouble(index)
        }

        return MeasurementSample(
            id = getLong(getColumnIndexOrThrow(MeasurementDbHelper.COL_ID)),
            source = MeasurementSourceType.valueOf(getString(getColumnIndexOrThrow(MeasurementDbHelper.COL_SOURCE))),
            timestampEpochMs = getLong(getColumnIndexOrThrow(MeasurementDbHelper.COL_TIMESTAMP)),
            success = getInt(getColumnIndexOrThrow(MeasurementDbHelper.COL_SUCCESS)) != 0,
            downloadMbps = doubleOrNull(MeasurementDbHelper.COL_DOWNLOAD_MBPS),
            uploadMbps = doubleOrNull(MeasurementDbHelper.COL_UPLOAD_MBPS),
            latencyMs = doubleOrNull(MeasurementDbHelper.COL_LATENCY_MS),
            jitterMs = doubleOrNull(MeasurementDbHelper.COL_JITTER_MS),
            packetLossPercent = doubleOrNull(MeasurementDbHelper.COL_PACKET_LOSS_PERCENT),
            bufferbloatMs = doubleOrNull(MeasurementDbHelper.COL_BUFFERBLOAT_MS),
            errorMessage = getColumnIndexOrThrow(MeasurementDbHelper.COL_ERROR_MESSAGE).let { index ->
                if (isNull(index)) null else getString(index)
            },
        )
    }
}
