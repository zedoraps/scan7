package net.moesli.scan7.training

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saved training scans on the device: number of JPEGs in `Pictures/Scan7 Training` and their size. */
data class TrainingStats(val scans: Int, val bytes: Long)

/**
 * Counts the training JPEGs via MediaStore (IO dispatcher). Without a media read permission this
 * sees only the files this install saved itself (scoped storage).
 */
suspend fun readTrainingStats(context: Context): Result<TrainingStats> = withContext(Dispatchers.IO) {
    runCatching {
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        // RELATIVE_PATH is stored with a trailing slash ("Pictures/Scan7 Training/").
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf("${Environment.DIRECTORY_PICTURES}/$TRAINING_DIR/")
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(uri, projection, selection, args, null).use { cursor ->
            var scans = 0
            var bytes = 0L
            if (cursor != null) {
                val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    scans++
                    bytes += cursor.getLong(size)
                }
            }
            TrainingStats(scans, bytes)
        }
    }
}
