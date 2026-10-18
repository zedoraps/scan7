package net.moesli.scan7.training

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.detection.MODEL_VERSION_ASSET
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.ui.scan.ScanCapture
import net.moesli.scan7.ui.scan.ScanOutcome
import net.moesli.scan7.ui.scan.ScanSource

private const val TAG = "TrainingSaver"

/** Folder name under Pictures/ (JPEG) and Documents/ (JSON). */
const val TRAINING_DIR = "Scan7 Training"

/** Where a sample went: the JPEG and the JSON sidecar (MediaStore uris, owned by the app). */
data class SavedSample(val imageUri: Uri, val jsonUri: Uri, val stem: String, val timestampMillis: Long)

/**
 * "Save scans for training": writes the frozen upright frame as JPEG (quality 95) to
 * `Pictures/Scan7 Training/` and a JSON sidecar to `Documents/Scan7 Training/` via MediaStore
 * (no storage permission on API 29+). IO runs on its own process-wide scope, so a save started when
 * the user confirms keeps going after the scan screen is gone; failures only show a toast.
 */
object TrainingSaver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Builds the sample for [capture], optionally with the confirmed [outcome]. */
    fun sample(context: Context, capture: ScanCapture, outcome: ScanOutcome?, now: Long = System.currentTimeMillis()): TrainingSample =
        TrainingSample(
            timestampMillis = now,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            modelVersion = modelVersion(context),
            zoomRatio = capture.meta.zoomRatio,
            rotationDegrees = capture.meta.rotationDegrees,
            imageWidth = capture.bitmap.width,
            imageHeight = capture.bitmap.height,
            backend = capture.meta.backend,
            threshold = capture.meta.threshold,
            origin = capture.meta.origin,
            detections = capture.detections,
            outcome = outcome?.toTrainingOutcome(),
        )

    /**
     * Saves [capture] (with [outcome] if confirmed) in the background. With [existing] (the same
     * capture was saved before, e.g. via the review's "Save for training") only its JSON is
     * rewritten with the outcome, so the image isn't stored twice. [onSaved] runs on the main thread.
     */
    fun saveAsync(
        context: Context,
        capture: ScanCapture,
        outcome: ScanOutcome?,
        existing: SavedSample? = null,
        onSaved: (SavedSample) -> Unit = {},
    ) {
        val app = context.applicationContext
        val sample = sample(app, capture, outcome)
        scope.launch {
            runCatching {
                if (existing != null) {
                    writeJson(app, existing.jsonUri, sample.copy(timestampMillis = existing.timestampMillis))
                    existing
                } else {
                    save(app, capture.bitmap, sample)
                }
            }.onSuccess { saved ->
                withContext(Dispatchers.Main) { onSaved(saved) }
            }.onFailure {
                Log.w(TAG, "Saving the training sample failed", it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Couldn't save the scan for training", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun save(context: Context, bitmap: Bitmap, sample: TrainingSample): SavedSample {
        val stem = sample.fileStem()
        val resolver = context.contentResolver
        val image = insert(
            context,
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "$stem.jpg",
            "image/jpeg",
            "${Environment.DIRECTORY_PICTURES}/$TRAINING_DIR",
        )
        try {
            resolver.openOutputStream(image)!!.use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) { "JPEG encoding failed" }
            }
            publish(context, image)
        } catch (e: Exception) {
            resolver.delete(image, null, null)
            throw e
        }
        val json = insert(
            context,
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "$stem.json",
            "application/json",
            "${Environment.DIRECTORY_DOCUMENTS}/$TRAINING_DIR",
        )
        try {
            writeJson(context, json, sample)
            publish(context, json)
        } catch (e: Exception) {
            resolver.delete(json, null, null)
            throw e
        }
        Log.i(TAG, "Saved $stem (${bitmap.width}×${bitmap.height}, ${sample.detections.size} detections)")
        return SavedSample(image, json, stem, sample.timestampMillis)
    }

    private fun insert(context: Context, collection: Uri, name: String, mime: String, relativePath: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return checkNotNull(context.contentResolver.insert(collection, values)) { "MediaStore insert failed for $name" }
    }

    private fun publish(context: Context, uri: Uri) {
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    private fun writeJson(context: Context, uri: Uri, sample: TrainingSample) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sample.toJson().toByteArray()) }
    }

    private fun modelVersion(context: Context): String? = runCatching {
        context.assets.open(MODEL_VERSION_ASSET).bufferedReader().use { it.readLine()?.trim() }
    }.getOrNull()
}

private fun ScanOutcome.toTrainingOutcome(): TrainingOutcome = when (this) {
    is ScanOutcome.Scored -> TrainingOutcome("scored", source.trainingName(), cards.labels(), ignored.labels(), score.total)
    is ScanOutcome.Bust -> TrainingOutcome("bust", source.trainingName(), cards.labels(), ignored.labels(), 0)
}

private fun ScanSource.trainingName(): String = if (this == ScanSource.SCAN) "scan" else "manual_after_scan"

private fun List<Card>.labels(): List<String> = map { it.label }
