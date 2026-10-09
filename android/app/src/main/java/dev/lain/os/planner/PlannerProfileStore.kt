package dev.lain.os.planner

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.RandomAccessFile

internal class PlannerProfileStoreException(
    val code: String,
    cause: Throwable? = null,
) : Exception(code, cause)

internal class PlannerProfileStore internal constructor(private val root: File) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, STORE_DIRECTORY)
    )

    private val stateFile: File
    private val lockFile: File

    init {
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) {
            throw PlannerProfileStoreException(ERROR_STORAGE_FAILED)
        }
        if (!root.isDirectory) throw PlannerProfileStoreException(ERROR_STORAGE_FAILED)
        stateFile = File(root, STATE_FILE)
        lockFile = File(root, LOCK_FILE)
    }

    fun list(): List<PlannerProfile> = withLock {
        val state = readStateLocked()
        listOf(PlannerProfile.offlineDemo()) +
            state.profiles.values.sortedBy { it.name.lowercase() }
    }

    fun get(profileId: String): PlannerProfile? = withLock {
        if (profileId == PlannerProfile.DEMO_ID) PlannerProfile.offlineDemo()
        else readStateLocked().profiles[profileId]
    }

    fun active(): PlannerProfile = withLock {
        activeFrom(readStateLocked())
    }

    /** JSON passed to Python only to construct its already-existing trusted PlannerBinding. */
    fun activeBindingJson(): String = withLock {
        activeFrom(readStateLocked()).toTrustedBindingJson().toString()
    }

    fun upsert(profile: PlannerProfile) = withLock {
        require(profile.id != PlannerProfile.DEMO_ID) { "offline demo profile is built in" }
        val state = readStateLocked()
        val updated = LinkedHashMap(state.profiles)
        if (!updated.containsKey(profile.id)) {
            require(updated.size < MAX_PROFILES) { "too many planner profiles" }
        }
        updated[profile.id] = profile
        writeStateLocked(State(updated, state.activeProfileId))
    }

    fun select(profileId: String) = withLock {
        val state = readStateLocked()
        require(
            profileId == PlannerProfile.DEMO_ID || state.profiles.containsKey(profileId)
        ) { "planner profile does not exist" }
        writeStateLocked(State(state.profiles, profileId))
    }

    fun remove(profileId: String): Boolean = withLock {
        if (profileId == PlannerProfile.DEMO_ID) return@withLock false
        val state = readStateLocked()
        if (!state.profiles.containsKey(profileId)) return@withLock false
        val updated = LinkedHashMap(state.profiles)
        updated.remove(profileId)
        val active = if (state.activeProfileId == profileId) {
            PlannerProfile.DEMO_ID
        } else {
            state.activeProfileId
        }
        writeStateLocked(State(updated, active))
        true
    }

    private fun activeFrom(state: State): PlannerProfile =
        if (state.activeProfileId == PlannerProfile.DEMO_ID) {
            PlannerProfile.offlineDemo()
        } else {
            state.profiles[state.activeProfileId]
                ?: throw PlannerProfileStoreException(ERROR_STATE_INVALID)
        }

    private fun readStateLocked(): State {
        val atomic = AtomicFile(stateFile)
        if (!stateFile.exists()) return State(emptyMap(), PlannerProfile.DEMO_ID)
        return try {
            val payload = atomic.readFully()
            if (payload.isEmpty() || payload.size > MAX_STATE_BYTES) {
                throw PlannerProfileStoreException(ERROR_STATE_INVALID)
            }
            val tokener = JSONTokener(payload.toString(Charsets.UTF_8))
            val raw = tokener.nextValue()
            if (raw !is JSONObject || tokener.nextClean() != '\u0000') {
                throw PlannerProfileStoreException(ERROR_STATE_INVALID)
            }
            if (raw.keys().asSequence().toSet() != STATE_FIELDS) {
                throw PlannerProfileStoreException(ERROR_STATE_INVALID)
            }
            if (raw.get("version") !is Int || raw.getInt("version") !in setOf(1, STATE_VERSION)) {
                throw PlannerProfileStoreException(ERROR_STATE_INVALID)
            }
            val active = raw.get("active_profile_id")
            if (active !is String || active.isBlank()) {
                throw PlannerProfileStoreException(ERROR_STATE_INVALID)
            }
            val rawProfiles = raw.get("profiles")
            if (rawProfiles !is JSONArray || rawProfiles.length() > MAX_PROFILES) {
                throw PlannerProfileStoreException(ERROR_STATE_INVALID)
            }
            val profiles = LinkedHashMap<String, PlannerProfile>()
            for (index in 0 until rawProfiles.length()) {
                val item = rawProfiles.get(index)
                if (item !is JSONObject) throw PlannerProfileStoreException(ERROR_STATE_INVALID)
                val profile = PlannerProfile.fromStorageJson(item)
                if (profile.id == PlannerProfile.DEMO_ID || profiles.put(profile.id, profile) != null) {
                    throw PlannerProfileStoreException(ERROR_STATE_INVALID)
                }
            }
            if (active != PlannerProfile.DEMO_ID && !profiles.containsKey(active)) {
                throw PlannerProfileStoreException(ERROR_STATE_INVALID)
            }
            State(profiles, active)
        } catch (exc: PlannerProfileStoreException) {
            throw exc
        } catch (exc: Exception) {
            throw PlannerProfileStoreException(ERROR_STATE_INVALID, exc)
        }
    }

    private fun writeStateLocked(state: State) {
        val profiles = JSONArray()
        state.profiles.values.sortedBy { it.id }.forEach { profiles.put(it.toStorageJson()) }
        val payload = JSONObject()
            .put("version", STATE_VERSION)
            .put("active_profile_id", state.activeProfileId)
            .put("profiles", profiles)
            .toString()
            .toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_STATE_BYTES) {
            throw PlannerProfileStoreException(ERROR_STORAGE_FAILED)
        }

        val atomic = AtomicFile(stateFile)
        val stream = try {
            atomic.startWrite()
        } catch (exc: Exception) {
            throw PlannerProfileStoreException(ERROR_STORAGE_FAILED, exc)
        }
        try {
            stream.write(payload)
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (exc: Exception) {
            atomic.failWrite(stream)
            throw PlannerProfileStoreException(ERROR_STORAGE_FAILED, exc)
        }
    }

    private fun <T> withLock(block: () -> T): T = synchronized(PROCESS_LOCK) {
        try {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                val lock = channel.lock()
                try {
                    block()
                } finally {
                    lock.release()
                }
            }
        } catch (exc: PlannerProfileStoreException) {
            throw exc
        } catch (exc: IllegalArgumentException) {
            throw exc
        } catch (exc: Exception) {
            throw PlannerProfileStoreException(ERROR_STORAGE_FAILED, exc)
        }
    }

    private data class State(
        val profiles: Map<String, PlannerProfile>,
        val activeProfileId: String,
    )

    companion object {
        const val ERROR_STATE_INVALID = "PLANNER_PROFILE_STATE_INVALID"
        const val ERROR_STORAGE_FAILED = "PLANNER_PROFILE_STORAGE_FAILED"

        private const val STORE_DIRECTORY = "planner-profiles"
        private const val STATE_FILE = "profiles.json"
        private const val LOCK_FILE = ".profiles.lock"
        private const val STATE_VERSION = 2
        private const val MAX_PROFILES = 32
        private const val MAX_STATE_BYTES = 512 * 1024
        private val PROCESS_LOCK = Any()
        private val STATE_FIELDS = setOf("version", "active_profile_id", "profiles")
    }
}
