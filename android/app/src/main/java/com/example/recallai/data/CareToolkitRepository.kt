package com.example.recallai.data

import android.content.Context
import androidx.core.content.edit
import com.example.recallai.data.local.PatientAlarmDao
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.ReminderDao
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.data.local.ReminderStatus
import com.example.recallai.data.local.SavedFaceDao
import com.example.recallai.data.local.SavedFaceEntity
import com.example.recallai.reminders.PatientAlarmScheduler
import com.example.recallai.reminders.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.FaceProfileDto
import com.example.recallai.data.remote.FaceProfilesPutRequest
import com.example.recallai.data.remote.AlarmDto
import com.example.recallai.data.remote.ConsentDto
import com.example.recallai.data.remote.KnownPersonDto
import com.example.recallai.data.remote.MedicationDto
import com.example.recallai.data.remote.PatientToolkitPutRequest
import com.example.recallai.data.remote.PeopleDirectoryPutRequest
import com.example.recallai.data.remote.ReminderDto
import com.example.recallai.data.remote.RoutineDto
import com.example.recallai.face.FaceDescriptor

data class MedicationItem(
    val id: String,
    val name: String,
    val timeLabel: String,
    val notes: String = "",
    val takenToday: Boolean = false,
    val takenAt: Long? = null,
    val snoozeCount: Int = 0,
    val skippedToday: Boolean = false,
    val skipReason: String = "",
    val adherenceStatus: String = "PENDING",
    val lastResetDate: String = "",
    val updatedAt: Long = 0L
)

data class KnownPersonItem(
    val id: String,
    val name: String,
    val relation: String = "",
    val note: String = "",
    val phone: String = "",
    val updatedAt: Long = 0L
)

data class FaceProfileItem(
    val id: String,
    val name: String,
    val vector: List<Float>,
    val updatedAt: Long = System.currentTimeMillis()
)

data class RoutineTaskItem(
    val id: String,
    val title: String,
    val period: String,
    val frequency: String = "Daily",
    val timeLabel: String = "",
    val doneToday: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val streakDays: Int = 0,
    val lastCompletedDate: String = ""
)

data class CareTaskItem(
    val id: String,
    val title: String,
    val assignee: String = "Patient",
    val dueLabel: String = "Today",
    val scheduleTimeLabel: String = "",
    val isDone: Boolean = false,
    val priority: String = "MEDIUM"
)

data class ConsentSettings(
    val shareWithCaregiver: Boolean = true,
    val allowLocationSharing: Boolean = true,
    val allowVoiceStorage: Boolean = true,
    val allowPhotoStorage: Boolean = true,
    val updatedAt: Long = 0L
)

data class PatientLiveLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ManagedSafeZone(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float
)

@Singleton
class CareToolkitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedFaceDao: SavedFaceDao,
    private val reminderDao: ReminderDao,
    private val patientAlarmDao: PatientAlarmDao
) {
    private val prefs = context.getSharedPreferences("care_toolkit_prefs", Context.MODE_PRIVATE)
    private val faceMigrateMutex = Mutex()
    private var faceMigrated: Boolean = false

    fun getMedications(): List<MedicationItem> {
        val today = todayDateString()
        val raw = prefs.getString(KEY_MEDS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val items = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    MedicationItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        timeLabel = o.optString("timeLabel"),
                        notes = o.optString("notes"),
                        takenToday = o.optBoolean("takenToday", false),
                        takenAt = if (o.has("takenAt") && !o.isNull("takenAt")) o.optLong("takenAt") else null,
                        snoozeCount = o.optInt("snoozeCount", 0),
                        skippedToday = o.optBoolean("skippedToday", false),
                        skipReason = o.optString("skipReason"),
                        adherenceStatus = o.optString("adherenceStatus", "PENDING"),
                        lastResetDate = o.optString("lastResetDate", ""),
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                )
            }
        }
        // Reset per-day fields whenever a new calendar day starts
        if (items.none { it.lastResetDate != today }) return items
        val reset = items.map { item ->
            if (item.lastResetDate != today) {
                item.copy(
                    takenToday = false,
                    skippedToday = false,
                    skipReason = "",
                    snoozeCount = 0,
                    adherenceStatus = "PENDING",
                    takenAt = null,
                    lastResetDate = today,
                    updatedAt = System.currentTimeMillis()
                )
            } else item
        }
        saveMedicationsLocal(reset)
        return reset
    }

    suspend fun saveMedications(items: List<MedicationItem>) = withContext(Dispatchers.IO) {
        saveMedicationsLocal(items)
        pushPatientToolkitToServer()
    }

    private fun saveMedicationsLocal(items: List<MedicationItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("timeLabel", item.timeLabel)
                    .put("notes", item.notes)
                    .put("takenToday", item.takenToday)
                    .put("takenAt", item.takenAt ?: JSONObject.NULL)
                    .put("snoozeCount", item.snoozeCount)
                    .put("skippedToday", item.skippedToday)
                    .put("skipReason", item.skipReason)
                    .put("adherenceStatus", item.adherenceStatus)
                    .put("lastResetDate", item.lastResetDate)
                    .put("updatedAt", item.updatedAt)
            )
        }
        prefs.edit { putString(KEY_MEDS, arr.toString()) }
    }

    fun getKnownPeople(): List<KnownPersonItem> {
        val raw = prefs.getString(KEY_PEOPLE, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    KnownPersonItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        relation = o.optString("relation"),
                        note = o.optString("note"),
                        phone = o.optString("phone"),
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                )
            }
        }
    }

    suspend fun saveKnownPeople(items: List<KnownPersonItem>) = withContext(Dispatchers.IO) {
        saveKnownPeopleLocal(items)
        pushKnownPeopleToServer(items)
    }

    private suspend fun pushKnownPeopleToServer(items: List<KnownPersonItem>) {
        runCatching {
            ApiClient.api.putPeopleDirectory(
                PeopleDirectoryPutRequest(people = items.map { it.toDto() })
            )
        }
    }

    private fun saveKnownPeopleLocal(items: List<KnownPersonItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("relation", item.relation)
                    .put("note", item.note)
                    .put("phone", item.phone)
                    .put("updatedAt", item.updatedAt)
            )
        }
        prefs.edit { putString(KEY_PEOPLE, arr.toString()) }
    }

    /** Pull cloud directory and merge with local entries (union by id; newer updatedAt wins). */
    suspend fun syncPeopleDirectoryFromServer(patientId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val remote = ApiClient.api.getPeopleDirectory(patientId).people
                val local = getKnownPeople()
                val merged = mergeKnownPeople(
                    remote.map { it.toItem() },
                    local
                )
                saveKnownPeopleLocal(merged)
                ApiClient.api.putPeopleDirectory(
                    PeopleDirectoryPutRequest(
                        people = merged.map { it.toDto() },
                        patientId = patientId
                    )
                )
                true
            }.getOrDefault(false)
        }

    private fun mergeKnownPeople(
        remote: List<KnownPersonItem>,
        local: List<KnownPersonItem>
    ): List<KnownPersonItem> {
        val byId = linkedMapOf<String, KnownPersonItem>()
        for (person in remote) {
            byId[person.id] = person
        }
        for (person in local) {
            val existing = byId[person.id]
            if (existing == null) {
                byId[person.id] = person
            } else {
                byId[person.id] = if (person.updatedAt >= existing.updatedAt) person else existing
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun KnownPersonItem.toDto() = KnownPersonDto(
        clientId = id,
        name = name,
        relation = relation,
        note = note,
        phone = phone,
        updatedAt = updatedAt
    )

    private fun KnownPersonDto.toItem() = KnownPersonItem(
        id = clientId,
        name = name,
        relation = relation,
        note = note,
        phone = phone,
        updatedAt = updatedAt
    )

    suspend fun loadFaceProfiles(): List<FaceProfileItem> = withContext(Dispatchers.IO) {
        ensureFaceMigrated()
        savedFaceDao.getAll().map { e ->
            FaceProfileItem(
                id = e.id,
                name = e.name,
                vector = parseVectorJson(e.embeddingJson),
                updatedAt = e.createdAt
            )
        }
    }

    suspend fun insertFaceProfileAlways(name: String, vector: List<Float>) = withContext(Dispatchers.IO) {
        if (name.isBlank() || vector.isEmpty()) return@withContext
        ensureFaceMigrated()
        val normalized = com.example.recallai.face.FaceVectorUtils.l2Normalize(vector)
        if (normalized.isEmpty()) return@withContext
        val json = vectorToJson(normalized)
        val trimmed = name.trim()
        savedFaceDao.insert(
            SavedFaceEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                embeddingJson = json,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun upsertFaceProfile(name: String, vector: List<Float>) = withContext(Dispatchers.IO) {
        if (name.isBlank() || vector.isEmpty()) return@withContext
        ensureFaceMigrated()
        val normalized = com.example.recallai.face.FaceVectorUtils.l2Normalize(vector)
        if (normalized.isEmpty()) return@withContext
        val json = vectorToJson(normalized)
        val trimmed = name.trim()
        val all = savedFaceDao.getAll()
        val existing = all.find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) {
            savedFaceDao.update(
                existing.copy(
                    name = trimmed,
                    embeddingJson = json,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            insertFaceProfileAlways(trimmed, vector)
        }
        pushFaceProfilesToServer()
    }

    suspend fun deleteFaceProfile(id: String) = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext
        ensureFaceMigrated()
        savedFaceDao.deleteById(id)
        pushFaceProfilesToServer()
    }

    suspend fun clearAllFaceProfiles() = withContext(Dispatchers.IO) {
        ensureFaceMigrated()
        savedFaceDao.deleteAll()
        pushFaceProfilesToServer()
    }

    /**
     * One profile per display name — removes duplicate enrollments that break margin checks.
     */
    suspend fun replaceFaceProfile(name: String, vector: List<Float>) = withContext(Dispatchers.IO) {
        if (name.isBlank() || vector.isEmpty()) return@withContext
        ensureFaceMigrated()
        val normalized = com.example.recallai.face.FaceVectorUtils.l2Normalize(vector)
        if (normalized.isEmpty()) return@withContext
        val trimmed = name.trim()
        val json = vectorToJson(normalized)
        val all = savedFaceDao.getAll()
        val existing = all.find { it.name.equals(trimmed, ignoreCase = true) }
        val profileId = existing?.id ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        all.filter { it.name.equals(trimmed, ignoreCase = true) }.forEach { savedFaceDao.deleteById(it.id) }
        savedFaceDao.insert(
            SavedFaceEntity(
                id = profileId,
                name = trimmed,
                embeddingJson = json,
                createdAt = now
            )
        )
        pushFaceProfilesToServer()
    }

    /** Clears stored face templates when the on-device descriptor schema changes. */
    suspend fun migrateFaceDescriptorSchema(schemaVersion: Int): Boolean = withContext(Dispatchers.IO) {
        ensureFaceMigrated()
        val stored = prefs.getInt(KEY_FACE_DESCRIPTOR_VERSION, 0)
        if (stored >= schemaVersion) return@withContext false
        savedFaceDao.deleteAll()
        prefs.edit { putInt(KEY_FACE_DESCRIPTOR_VERSION, schemaVersion) }
        pushFaceProfilesToServer()
        true
    }

    /** Pull cloud face templates and merge with local (union by id; newer updatedAt wins). */
    suspend fun syncFaceProfilesFromServer(patientId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val remoteResp = ApiClient.api.getFaceProfiles(patientId)
                if (remoteResp.descriptorSchemaVersion != FaceDescriptor.SCHEMA_VERSION) {
                    return@runCatching false
                }
                val remote = remoteResp.profiles.map { it.toItem() }
                val local = loadFaceProfilesLocal()
                val merged = mergeFaceProfiles(remote, local)
                saveFaceProfilesLocal(merged)
                ApiClient.api.putFaceProfiles(
                    FaceProfilesPutRequest(
                        profiles = merged.map { it.toDto() },
                        descriptorSchemaVersion = FaceDescriptor.SCHEMA_VERSION,
                        patientId = patientId
                    )
                )
                true
            }.getOrDefault(false)
        }

    private suspend fun loadFaceProfilesLocal(): List<FaceProfileItem> {
        ensureFaceMigrated()
        return savedFaceDao.getAll().map { e ->
            FaceProfileItem(
                id = e.id,
                name = e.name,
                vector = parseVectorJson(e.embeddingJson),
                updatedAt = e.createdAt
            )
        }
    }

    private suspend fun saveFaceProfilesLocal(profiles: List<FaceProfileItem>) {
        ensureFaceMigrated()
        savedFaceDao.deleteAll()
        profiles.forEach { item ->
            val normalized = com.example.recallai.face.FaceVectorUtils.l2Normalize(item.vector)
            if (normalized.isEmpty() || item.id.isBlank() || item.name.isBlank()) return@forEach
            savedFaceDao.insert(
                SavedFaceEntity(
                    id = item.id,
                    name = item.name.trim(),
                    embeddingJson = vectorToJson(normalized),
                    createdAt = item.updatedAt
                )
            )
        }
    }

    private suspend fun pushFaceProfilesToServer(patientId: String? = null) {
        runCatching {
            val profiles = loadFaceProfilesLocal()
            ApiClient.api.putFaceProfiles(
                FaceProfilesPutRequest(
                    profiles = profiles.map { it.toDto() },
                    descriptorSchemaVersion = FaceDescriptor.SCHEMA_VERSION,
                    patientId = patientId
                )
            )
        }
    }

    private fun mergeFaceProfiles(
        remote: List<FaceProfileItem>,
        local: List<FaceProfileItem>
    ): List<FaceProfileItem> {
        val byId = linkedMapOf<String, FaceProfileItem>()
        for (profile in remote) {
            byId[profile.id] = profile
        }
        for (profile in local) {
            val existing = byId[profile.id]
            if (existing == null) {
                byId[profile.id] = profile
            } else {
                byId[profile.id] = if (profile.updatedAt >= existing.updatedAt) profile else existing
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun FaceProfileItem.toDto() = FaceProfileDto(
        clientId = id,
        name = name,
        embedding = vector,
        updatedAt = updatedAt
    )

    private fun FaceProfileDto.toItem() = FaceProfileItem(
        id = clientId,
        name = name,
        vector = embedding,
        updatedAt = updatedAt
    )

    private suspend fun ensureFaceMigrated() {
        faceMigrateMutex.withLock {
            if (faceMigrated) return
            if (savedFaceDao.count() > 0) {
                faceMigrated = true
                return
            }
            val legacy = readFaceProfilesFromPrefs()
            legacy.forEach { item ->
                if (item.id.isNotBlank() && item.vector.isNotEmpty()) {
                    savedFaceDao.insert(
                        SavedFaceEntity(
                            id = item.id.ifBlank { UUID.randomUUID().toString() },
                            name = item.name,
                            embeddingJson = vectorToJson(item.vector),
                            createdAt = item.updatedAt
                        )
                    )
                }
            }
            prefs.edit { putString(KEY_FACE_PROFILES, "[]") }
            faceMigrated = true
        }
    }

    private fun readFaceProfilesFromPrefs(): List<FaceProfileItem> {
        val raw = prefs.getString(KEY_FACE_PROFILES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val vectorArr = o.optJSONArray("vector") ?: JSONArray()
                val vector = buildList {
                    for (j in 0 until vectorArr.length()) {
                        add(vectorArr.optDouble(j, 0.0).toFloat())
                    }
                }
                add(
                    FaceProfileItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        vector = vector,
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun parseVectorJson(json: String): List<Float> {
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (j in 0 until arr.length()) {
                    add(arr.optDouble(j, 0.0).toFloat())
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun vectorToJson(vector: List<Float>): String {
        val a = JSONArray()
        vector.forEach { a.put(it.toDouble()) }
        return a.toString()
    }

    fun getRoutineTasks(): List<RoutineTaskItem> {
        val today = todayDateString()
        val raw = prefs.getString(KEY_ROUTINES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val tasks = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    RoutineTaskItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        period = o.optString("period", "Morning"),
                        frequency = o.optString("frequency", "Daily"),
                        timeLabel = o.optString("timeLabel"),
                        doneToday = o.optBoolean("doneToday", false),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        streakDays = o.optInt("streakDays", 0),
                        lastCompletedDate = o.optString("lastCompletedDate")
                    )
                )
            }
        }
        // Reset doneToday when the task's frequency period has rolled over
        if (tasks.none { it.doneToday && shouldRoutineReset(it, today) }) return tasks
        val reset = tasks.map { task ->
            if (task.doneToday && shouldRoutineReset(task, today)) {
                task.copy(doneToday = false, updatedAt = System.currentTimeMillis())
            } else task
        }
        saveRoutineTasksLocal(reset)
        return reset
    }

    private fun shouldRoutineReset(task: RoutineTaskItem, today: String): Boolean {
        val last = task.lastCompletedDate
        if (last.isEmpty() || last == today) return false
        return when (task.frequency) {
            "Daily" -> true
            "Weekly" -> runCatching {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val lastCal = Calendar.getInstance().apply { time = sdf.parse(last)!! }
                val todayCal = Calendar.getInstance().apply { time = sdf.parse(today)!! }
                lastCal.get(Calendar.WEEK_OF_YEAR) != todayCal.get(Calendar.WEEK_OF_YEAR) ||
                    lastCal.get(Calendar.YEAR) != todayCal.get(Calendar.YEAR)
            }.getOrElse { true }
            "Monthly" -> last.take(7) != today.take(7)
            else -> true
        }
    }

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    suspend fun saveRoutineTasks(items: List<RoutineTaskItem>) = withContext(Dispatchers.IO) {
        saveRoutineTasksLocal(items)
        pushPatientToolkitToServer()
    }

    private fun saveRoutineTasksLocal(items: List<RoutineTaskItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("period", item.period)
                    .put("frequency", item.frequency)
                    .put("timeLabel", item.timeLabel)
                    .put("doneToday", item.doneToday)
                    .put("updatedAt", item.updatedAt)
                    .put("streakDays", item.streakDays)
                    .put("lastCompletedDate", item.lastCompletedDate)
            )
        }
        prefs.edit { putString(KEY_ROUTINES, arr.toString()) }
    }

    fun getCareTasks(): List<CareTaskItem> {
        val raw = prefs.getString(KEY_CARE_TASKS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CareTaskItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        assignee = o.optString("assignee", "Patient"),
                        scheduleTimeLabel = o.optString("scheduleTimeLabel", ""),
                        isDone = o.optBoolean("isDone", false),
                        priority = o.optString("priority", "MEDIUM")
                    )
                )
            }
        }
    }

    fun saveCareTasks(items: List<CareTaskItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("assignee", item.assignee)
                    .put("dueLabel", item.dueLabel)
                    .put("scheduleTimeLabel", item.scheduleTimeLabel)
                    .put("isDone", item.isDone)
                    .put("priority", item.priority)
            )
        }
        prefs.edit { putString(KEY_CARE_TASKS, arr.toString()) }
    }

    fun getConsentSettings(): ConsentSettings {
        val raw = prefs.getString(KEY_CONSENT, "{}") ?: "{}"
        val o = JSONObject(raw)
        return ConsentSettings(
            shareWithCaregiver = o.optBoolean("shareWithCaregiver", true),
            allowLocationSharing = o.optBoolean("allowLocationSharing", true),
            allowVoiceStorage = o.optBoolean("allowVoiceStorage", true),
            allowPhotoStorage = o.optBoolean("allowPhotoStorage", true),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }

    suspend fun saveConsentSettings(settings: ConsentSettings) = withContext(Dispatchers.IO) {
        val stamped = settings.copy(updatedAt = System.currentTimeMillis())
        saveConsentSettingsLocal(stamped)
        pushPatientToolkitToServer()
    }

    private fun saveConsentSettingsLocal(settings: ConsentSettings) {
        val o = JSONObject()
            .put("shareWithCaregiver", settings.shareWithCaregiver)
            .put("allowLocationSharing", settings.allowLocationSharing)
            .put("allowVoiceStorage", settings.allowVoiceStorage)
            .put("allowPhotoStorage", settings.allowPhotoStorage)
            .put("updatedAt", settings.updatedAt)
        prefs.edit { putString(KEY_CONSENT, o.toString()) }
    }

    fun getManagedZones(): List<ManagedSafeZone> {
        val raw = prefs.getString(KEY_MANAGED_ZONES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ManagedSafeZone(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        lat = o.optDouble("lat", 0.0),
                        lng = o.optDouble("lng", 0.0),
                        radiusMeters = o.optDouble("radiusMeters", 250.0).toFloat()
                    )
                )
            }
        }
    }

    fun saveManagedZones(items: List<ManagedSafeZone>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("lat", item.lat)
                    .put("lng", item.lng)
                    .put("radiusMeters", item.radiusMeters.toDouble())
            )
        }
        prefs.edit { putString(KEY_MANAGED_ZONES, arr.toString()) }
    }

    fun getOrCreateManagedZones(defaults: List<ManagedSafeZone>): List<ManagedSafeZone> {
        val existing = getManagedZones()
        if (existing.isNotEmpty()) return existing
        saveManagedZones(defaults)
        return defaults
    }

    fun upsertManagedZone(zone: ManagedSafeZone) {
        val current = getManagedZones().toMutableList()
        val idx = current.indexOfFirst { it.id == zone.id }
        if (idx >= 0) current[idx] = zone else current.add(zone)
        saveManagedZones(current)
    }

    fun removeManagedZone(id: String) {
        val current = getManagedZones().filterNot { it.id == id }
        saveManagedZones(current)
    }

    fun savePatientLiveLocation(location: PatientLiveLocation) {
        val o = JSONObject()
            .put("lat", location.lat)
            .put("lng", location.lng)
            .put("updatedAt", location.updatedAt)
        prefs.edit { putString(KEY_PATIENT_LIVE_LOCATION, o.toString()) }
    }

    fun getPatientLiveLocation(): PatientLiveLocation? {
        val raw = prefs.getString(KEY_PATIENT_LIVE_LOCATION, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            PatientLiveLocation(
                lat = o.optDouble("lat", 0.0),
                lng = o.optDouble("lng", 0.0),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    /** Pull medications, routines, and consent; merge with local (newer updatedAt wins). */
    suspend fun syncPatientToolkitFromServer(patientId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val remote = ApiClient.api.getPatientToolkit(patientId)
                val mergedMeds = mergeMedications(
                    remote.medications.map { it.toItem() },
                    getMedications()
                )
                val mergedRoutines = mergeRoutines(
                    remote.routines.map { it.toItem() },
                    getRoutineTasks()
                )
                val mergedConsent = mergeConsent(
                    remote.consent.toItem(),
                    getConsentSettings()
                )
                val mergedReminders = mergeReminders(
                    remote.reminders.map { it.toReminderEntity() },
                    reminderDao.getAll()
                )
                val mergedAlarms = mergeAlarms(
                    remote.alarms.map { it.toAlarmEntity() },
                    patientAlarmDao.getAll()
                )
                saveMedicationsLocal(mergedMeds)
                saveRoutineTasksLocal(mergedRoutines)
                saveConsentSettingsLocal(mergedConsent)
                applyMergedRemindersToLocal(mergedReminders)
                applyMergedAlarmsToLocal(mergedAlarms)
                ApiClient.api.putPatientToolkit(
                    buildPatientToolkitPutRequest(
                        medications = mergedMeds,
                        routines = mergedRoutines,
                        consent = mergedConsent,
                        reminders = mergedReminders,
                        alarms = mergedAlarms,
                        patientId = patientId
                    )
                )
                true
            }.getOrDefault(false)
        }

    /** Push full toolkit including Room reminders and alarms. */
    suspend fun pushPatientToolkitToServer(patientId: String? = null) =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.api.putPatientToolkit(
                    buildPatientToolkitPutRequest(
                        medications = getMedications(),
                        routines = getRoutineTasks(),
                        consent = getConsentSettings(),
                        reminders = reminderDao.getAll(),
                        alarms = patientAlarmDao.getAll(),
                        patientId = patientId
                    )
                )
            }
        }

    private fun mergeMedications(
        remote: List<MedicationItem>,
        local: List<MedicationItem>
    ): List<MedicationItem> {
        val byId = linkedMapOf<String, MedicationItem>()
        for (item in remote) byId[item.id] = item
        for (item in local) {
            val existing = byId[item.id]
            byId[item.id] = when {
                existing == null -> item
                item.updatedAt >= existing.updatedAt -> item
                else -> existing
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun mergeRoutines(
        remote: List<RoutineTaskItem>,
        local: List<RoutineTaskItem>
    ): List<RoutineTaskItem> {
        val byId = linkedMapOf<String, RoutineTaskItem>()
        for (item in remote) byId[item.id] = item
        for (item in local) {
            val existing = byId[item.id]
            byId[item.id] = when {
                existing == null -> item
                item.updatedAt >= existing.updatedAt -> item
                else -> existing
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun mergeConsent(remote: ConsentSettings, local: ConsentSettings): ConsentSettings {
        return if (local.updatedAt >= remote.updatedAt) local else remote
    }

    private fun mergeReminders(
        remote: List<ReminderEntity>,
        local: List<ReminderEntity>
    ): List<ReminderEntity> {
        val byId = linkedMapOf<String, ReminderEntity>()
        for (item in remote) byId[item.clientId] = item
        for (item in local) {
            val existing = byId[item.clientId]
            byId[item.clientId] = when {
                existing == null -> item
                item.updatedAt >= existing.updatedAt -> item
                else -> existing
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private fun mergeAlarms(
        remote: List<PatientAlarmEntity>,
        local: List<PatientAlarmEntity>
    ): List<PatientAlarmEntity> {
        val byId = linkedMapOf<String, PatientAlarmEntity>()
        for (item in remote) byId[item.clientId] = item.copy(pendingAckSinceMs = null)
        for (item in local) {
            val existing = byId[item.clientId]
            val normalized = item.copy(pendingAckSinceMs = null)
            byId[item.clientId] = when {
                existing == null -> normalized
                normalized.updatedAt >= existing.updatedAt -> normalized
                else -> existing
            }
        }
        return byId.values.sortedByDescending { it.updatedAt }
    }

    private suspend fun applyMergedRemindersToLocal(merged: List<ReminderEntity>) {
        val keepIds = merged.map { it.clientId }.toSet()
        reminderDao.getAll()
            .filter { it.clientId !in keepIds }
            .forEach {
                ReminderScheduler.cancel(context, it.id)
                reminderDao.delete(it)
            }
        merged.forEach { item ->
            val local = reminderDao.getByClientId(item.clientId)
            val toSave = if (local != null) item.copy(id = local.id) else item.copy(id = 0)
            val rowId = reminderDao.upsert(toSave)
            val saved = reminderDao.getById(rowId) ?: reminderDao.getByClientId(item.clientId) ?: return@forEach
            if (saved.status == ReminderStatus.PENDING) {
                ReminderScheduler.schedule(context, saved)
            } else {
                ReminderScheduler.cancel(context, saved.id)
            }
        }
    }

    private suspend fun applyMergedAlarmsToLocal(merged: List<PatientAlarmEntity>) {
        val keepIds = merged.map { it.clientId }.toSet()
        patientAlarmDao.getAll()
            .filter { it.clientId !in keepIds }
            .forEach {
                PatientAlarmScheduler.cancelFireAndGrace(context, it.id)
                patientAlarmDao.delete(it)
            }
        merged.forEach { item ->
            val local = patientAlarmDao.getByClientId(item.clientId)
            val toSave = (if (local != null) item.copy(id = local.id) else item.copy(id = 0))
                .copy(pendingAckSinceMs = null)
            val rowId = if (local != null) {
                patientAlarmDao.update(toSave)
                local.id
            } else {
                patientAlarmDao.insert(toSave)
            }
            val saved = patientAlarmDao.getById(rowId) ?: return@forEach
            if (saved.enabled) {
                PatientAlarmScheduler.schedule(context, saved)
            } else {
                PatientAlarmScheduler.cancelFireAndGrace(context, saved.id)
            }
        }
    }

    private fun buildPatientToolkitPutRequest(
        medications: List<MedicationItem>,
        routines: List<RoutineTaskItem>,
        consent: ConsentSettings,
        reminders: List<ReminderEntity>,
        alarms: List<PatientAlarmEntity>,
        patientId: String?
    ) = PatientToolkitPutRequest(
        medications = medications.map { it.toDto() },
        routines = routines.map { it.toDto() },
        consent = consent.toDto(),
        reminders = reminders.map { it.toReminderDto() },
        alarms = alarms.map { it.toAlarmDto() },
        patientId = patientId
    )

    /** Sync people, faces, and patient toolkit in one call (login / splash). */
    suspend fun syncAllCloudToolkit(patientId: String? = null) {
        syncPeopleDirectoryFromServer(patientId)
        syncFaceProfilesFromServer(patientId)
        syncPatientToolkitFromServer(patientId)
    }

    private fun MedicationItem.toDto() = MedicationDto(
        clientId = id,
        name = name,
        timeLabel = timeLabel,
        notes = notes,
        takenToday = takenToday,
        takenAt = takenAt,
        snoozeCount = snoozeCount,
        skippedToday = skippedToday,
        skipReason = skipReason,
        adherenceStatus = adherenceStatus,
        lastResetDate = lastResetDate,
        updatedAt = updatedAt
    )

    private fun MedicationDto.toItem() = MedicationItem(
        id = clientId,
        name = name,
        timeLabel = timeLabel,
        notes = notes,
        takenToday = takenToday,
        takenAt = takenAt,
        snoozeCount = snoozeCount,
        skippedToday = skippedToday,
        skipReason = skipReason,
        adherenceStatus = adherenceStatus,
        lastResetDate = lastResetDate,
        updatedAt = updatedAt
    )

    private fun RoutineTaskItem.toDto() = RoutineDto(
        clientId = id,
        title = title,
        period = period,
        frequency = frequency,
        timeLabel = timeLabel,
        doneToday = doneToday,
        streakDays = streakDays,
        lastCompletedDate = lastCompletedDate,
        updatedAt = updatedAt
    )

    private fun RoutineDto.toItem() = RoutineTaskItem(
        id = clientId,
        title = title,
        period = period,
        frequency = frequency,
        timeLabel = timeLabel,
        doneToday = doneToday,
        streakDays = streakDays,
        lastCompletedDate = lastCompletedDate,
        updatedAt = updatedAt
    )

    private fun ConsentSettings.toDto() = ConsentDto(
        shareWithCaregiver = shareWithCaregiver,
        allowLocationSharing = allowLocationSharing,
        allowVoiceStorage = allowVoiceStorage,
        allowPhotoStorage = allowPhotoStorage,
        updatedAt = updatedAt
    )

    private fun ConsentDto.toItem() = ConsentSettings(
        shareWithCaregiver = shareWithCaregiver,
        allowLocationSharing = allowLocationSharing,
        allowVoiceStorage = allowVoiceStorage,
        allowPhotoStorage = allowPhotoStorage,
        updatedAt = updatedAt
    )

    private fun ReminderEntity.toReminderDto() = ReminderDto(
        clientId = clientId,
        title = title,
        description = description.orEmpty(),
        datetime = datetime,
        status = status.name,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt,
        warn10Min = warn10Min,
        preset = preset.orEmpty(),
        repeatMode = repeatMode.name,
        daysOfWeekMask = daysOfWeekMask
    )

    private fun ReminderDto.toReminderEntity() = ReminderEntity(
        clientId = clientId,
        title = title,
        description = description.takeIf { it.isNotBlank() },
        datetime = datetime,
        status = runCatching { ReminderStatus.valueOf(status) }.getOrDefault(ReminderStatus.PENDING),
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt,
        warn10Min = warn10Min,
        preset = preset.takeIf { it.isNotBlank() },
        repeatMode = runCatching { ReminderRepeatMode.valueOf(repeatMode) }.getOrDefault(ReminderRepeatMode.NONE),
        daysOfWeekMask = daysOfWeekMask
    )

    private fun PatientAlarmEntity.toAlarmDto() = AlarmDto(
        clientId = clientId,
        label = label,
        hour = hour,
        minute = minute,
        repeatMode = repeatMode.name,
        daysOfWeekMask = daysOfWeekMask,
        enabled = enabled,
        nextTriggerAt = nextTriggerAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun AlarmDto.toAlarmEntity() = PatientAlarmEntity(
        clientId = clientId,
        label = label,
        hour = hour,
        minute = minute,
        repeatMode = runCatching { PatientAlarmRepeatMode.valueOf(repeatMode) }.getOrDefault(PatientAlarmRepeatMode.ONCE),
        daysOfWeekMask = daysOfWeekMask,
        enabled = enabled,
        nextTriggerAt = nextTriggerAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingAckSinceMs = null
    )

    companion object {
        private const val KEY_MEDS = "medications"
        private const val KEY_PEOPLE = "known_people"
        private const val KEY_FACE_PROFILES = "face_profiles"
        private const val KEY_FACE_DESCRIPTOR_VERSION = "face_descriptor_schema_version"
        private const val KEY_ROUTINES = "routine_tasks"
        private const val KEY_CARE_TASKS = "care_tasks"
        private const val KEY_CONSENT = "consent_settings"
        private const val KEY_MANAGED_ZONES = "managed_safe_zones"
        private const val KEY_PATIENT_LIVE_LOCATION = "patient_live_location"
    }
}

