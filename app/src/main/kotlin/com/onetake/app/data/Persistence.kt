package com.onetake.app.data

import android.content.Context
import android.net.Uri
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.PracticePiece
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// MARK: - App state persistence (JSON on disk, no backend — matches "nothing leaves this phone")

/** Everything AppStore needs to survive a relaunch. Media files themselves live under
 *  MediaStore; this only holds the structured data (and the URIs pointing at those files). */
data class PersistedState(
    val auditions: List<Audition> = emptyList(),
    val takes: List<Take> = emptyList(),
    val practice: List<PracticePiece> = emptyList(),
    val slate: SlateInfo = SlateInfo(),
    val isOnboarded: Boolean = false,
    val headshots: Map<String, String> = emptyMap(),
)

/** Hand-rolled org.json (de)serialization — no JSON library dependency exists in this project
 *  and a newly-added one can't be verified to resolve/build without an Android SDK locally, so
 *  this sticks to what's already on the classpath. */
object Persistence {
    private const val FILE_NAME = "state.json"
    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** Null when there's no files dir (plain-JVM unit tests) — never fall back to a relative path. */
    private fun stateFile(context: Context): File? = context.filesDir?.let { File(it, FILE_NAME) }

    /**
     * Returns null only when there is nothing usable on disk. A record that fails to parse is
     * skipped rather than failing the whole load — otherwise one bad field would start the app
     * empty and the next save would overwrite every audition with nothing. If the file can't be
     * read at all it's moved aside (state.corrupt-<time>.json) instead of being silently replaced.
     */
    fun load(context: Context): PersistedState? {
        val file = stateFile(context) ?: return null
        if (!file.exists()) return null
        val root = try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            runCatching { file.renameTo(File(file.parentFile, "state.corrupt-${System.currentTimeMillis()}.json")) }
            return null
        }
        val state = PersistedState(
            auditions = root.optJSONArray("auditions")?.mapObjects { it.toAudition() } ?: emptyList(),
            takes = root.optJSONArray("takes")?.mapObjects { it.toTake() } ?: emptyList(),
            practice = root.optJSONArray("practice")?.mapObjects { it.toPracticePiece() } ?: emptyList(),
            slate = root.optJSONObject("slate")?.let { runCatching { it.toSlateInfo() }.getOrNull() } ?: SlateInfo(),
            isOnboarded = root.optBoolean("isOnboarded", false),
            headshots = root.optJSONObject("headshots")?.let { runCatching { it.toStringMap() }.getOrNull() } ?: emptyMap(),
        )
        return rebaseMedia(state, context.filesDir)
    }

    /** Parses every object in the array, dropping (not failing on) any one that won't parse. */
    private fun <T> JSONArray.mapObjects(parse: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { i -> runCatching { parse(getJSONObject(i)) }.getOrNull() }

    /**
     * Media URIs are stored as absolute file:// paths. After a backup restore (another device or
     * user profile) the app's files dir can live at a different path, so re-point any URI that
     * was under a files dir at the same relative location under the current one.
     */
    private fun rebaseMedia(state: PersistedState, filesDir: File?): PersistedState {
        if (filesDir == null) return state
        fun rebase(uri: String?): String? {
            if (uri == null || !uri.startsWith("file:")) return uri
            val path = Uri.parse(uri).path ?: return uri
            if (path.startsWith(filesDir.absolutePath + "/")) return uri
            val marker = path.indexOf("/files/")
            if (marker < 0) return uri
            return Uri.fromFile(File(filesDir, path.substring(marker + "/files/".length))).toString()
        }
        return state.copy(
            auditions = state.auditions.map {
                it.copy(
                    slateVideoUri = rebase(it.slateVideoUri),
                    fullLengthVideoUri = rebase(it.fullLengthVideoUri),
                    exportedFileUri = rebase(it.exportedFileUri),
                )
            },
            takes = state.takes.map { it.copy(fileUri = rebase(it.fileUri)) },
            headshots = state.headshots.mapValues { (_, v) -> rebase(v) ?: v },
        )
    }

    fun save(context: Context, state: PersistedState) {
        try {
            val root = JSONObject()
            root.put("auditions", state.auditions.toJsonArray())
            root.put("takes", state.takes.toJsonArray())
            root.put("practice", state.practice.toJsonArray())
            root.put("slate", state.slate.toJson())
            root.put("isOnboarded", state.isOnboarded)
            root.put("headshots", JSONObject(state.headshots))
            val target = stateFile(context) ?: return
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
            // Best-effort — a failed save shouldn't crash the app; the in-memory state is
            // still correct for this session, just not persisted this time.
        }
    }

    // --- Audition ---

    private fun Audition.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("project", project)
        put("projectType", projectType)
        put("role", role)
        put("roleSize", roleSize)
        put("castingOffice", castingOffice)
        put("due", due.format(isoFmt))
        put("asks", JSONArray(asks))
        put("pieces", JSONArray(pieces.map { it.toJson() }))
        put("scenes", JSONArray(scenes.map { it.toJson() }))
        put("destination", destination.name)
        put("status", status.name)
        sentDate?.let { put("sentDate", it.format(isoFmt)) }
        put("headshotAsset", headshotAsset)
        slateVideoUri?.let { put("slateVideoUri", it) }
        fullLengthVideoUri?.let { put("fullLengthVideoUri", it) }
        exportedFileUri?.let { put("exportedFileUri", it) }
    }

    private fun JSONObject.toAudition(): Audition = Audition(
        id = getString("id"),
        project = getString("project"),
        projectType = getString("projectType"),
        role = getString("role"),
        roleSize = getString("roleSize"),
        castingOffice = getString("castingOffice"),
        due = LocalDateTime.parse(getString("due"), isoFmt),
        asks = optJSONArray("asks")?.toStringList() ?: emptyList(),
        pieces = optJSONArray("pieces")?.toPieceList() ?: emptyList(),
        scenes = optJSONArray("scenes")?.toScriptSceneList() ?: emptyList(),
        destination = runCatching { Destination.valueOf(getString("destination")) }.getOrDefault(Destination.EmailOrLink),
        status = runCatching { AuditionStatus.valueOf(getString("status")) }.getOrDefault(AuditionStatus.Due),
        sentDate = optString("sentDate", null)?.let { LocalDateTime.parse(it, isoFmt) },
        headshotAsset = optString("headshotAsset", "headshot"),
        slateVideoUri = optString("slateVideoUri", null),
        fullLengthVideoUri = optString("fullLengthVideoUri", null),
        exportedFileUri = optString("exportedFileUri", null),
    )

    private fun List<Audition>.toJsonArray() = JSONArray(map { it.toJson() })

    // --- Piece / ScriptScene / ScriptLine ---

    private fun Piece.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("title", title)
        put("detail", detail)
        put("isDone", isDone)
    }

    private fun JSONObject.toPiece(): Piece = Piece(
        id = getString("id"),
        kind = runCatching { PieceKind.valueOf(getString("kind")) }.getOrDefault(PieceKind.Scene),
        title = getString("title"),
        detail = getString("detail"),
        isDone = getBoolean("isDone"),
    )

    private fun JSONArray.toPieceList(): List<Piece> = List(length()) { getJSONObject(it).toPiece() }

    private fun ScriptLine.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("character", character); put("text", text); put("isMine", isMine)
    }

    private fun JSONObject.toScriptLine(): ScriptLine = ScriptLine(
        id = getString("id"), character = getString("character"), text = getString("text"), isMine = getBoolean("isMine"),
    )

    private fun ScriptScene.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("number", number); put("title", title); put("heading", heading)
        put("lines", JSONArray(lines.map { it.toJson() }))
        put("estimatedSeconds", estimatedSeconds)
    }

    private fun JSONObject.toScriptScene(): ScriptScene = ScriptScene(
        id = getString("id"), number = getInt("number"), title = getString("title"), heading = getString("heading"),
        lines = optJSONArray("lines")?.let { arr -> List(arr.length()) { arr.getJSONObject(it).toScriptLine() } } ?: emptyList(),
        estimatedSeconds = getInt("estimatedSeconds"),
    )

    private fun JSONArray.toScriptSceneList(): List<ScriptScene> = List(length()) { getJSONObject(it).toScriptScene() }

    // --- Take ---

    private fun Take.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        put("durationSeconds", durationSeconds)
        put("recordedAt", recordedAt.format(isoFmt))
        put("isCircled", isCircled)
        put("notes", JSONArray(notes))
        fileUri?.let { put("fileUri", it) }
        auditionId?.let { put("auditionId", it) }
        sceneIndex?.let { put("sceneIndex", it) }
    }

    private fun JSONObject.toTake(): Take = Take(
        id = getString("id"),
        number = getInt("number"),
        durationSeconds = getInt("durationSeconds"),
        recordedAt = LocalDateTime.parse(getString("recordedAt"), isoFmt),
        isCircled = getBoolean("isCircled"),
        notes = optJSONArray("notes")?.toStringList() ?: emptyList(),
        fileUri = optString("fileUri", null),
        auditionId = optString("auditionId", null),
        sceneIndex = if (has("sceneIndex") && !isNull("sceneIndex")) getInt("sceneIndex") else null,
    )

    private fun List<Take>.toJsonArray() = JSONArray(map { it.toJson() })

    // --- PracticePiece ---

    private fun PracticePiece.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("detail", detail)
        put("linesLearned", linesLearned); put("linesTotal", linesTotal); put("character", character)
        scene?.let { put("scene", it.toJson()) }
        put("takesRecorded", takesRecorded)
        auditionId?.let { put("auditionId", it) }
        sceneIndex?.let { put("sceneIndex", it) }
    }

    private fun JSONObject.toPracticePiece(): PracticePiece = PracticePiece(
        id = getString("id"), title = getString("title"), detail = getString("detail"),
        linesLearned = getInt("linesLearned"), linesTotal = getInt("linesTotal"),
        character = optString("character", ""),
        scene = optJSONObject("scene")?.toScriptScene(),
        takesRecorded = optInt("takesRecorded", 0),
        auditionId = optString("auditionId", null),
        sceneIndex = if (has("sceneIndex") && !isNull("sceneIndex")) getInt("sceneIndex") else null,
    )

    private fun List<PracticePiece>.toJsonArray() = JSONArray(map { it.toJson() })

    // --- SlateInfo ---

    private fun SlateInfo.toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("height", height); put("basedIn", basedIn); put("representation", representation)
    }

    private fun JSONObject.toSlateInfo(): SlateInfo = SlateInfo(
        name = optString("name", ""), height = optString("height", ""),
        basedIn = optString("basedIn", ""), representation = optString("representation", ""),
    )

    // --- small helpers ---

    private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val it = keys()
        while (it.hasNext()) {
            val k = it.next()
            map[k] = getString(k)
        }
        return map
    }

    private fun JSONObject.optString(name: String, fallback: String?): String? =
        if (has(name) && !isNull(name)) getString(name) else fallback
}
