package dev.lain.os.runtime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class VolumeReading(val index: Int, val min: Int, val max: Int, val muted: Boolean) {
    fun json(): JSONObject {
        require(min >= 0 && index >= min && index <= max)
        return JSONObject().put("index", index).put("min_index", min).put("max_index", max).put("muted", muted)
    }
}
internal data class BrightnessReading(val value: Int, val mode: String)
internal data class LaunchableApp(val packageName: String, val label: String)
internal class DeviceControlFailure(val status: String, val reason: String) : RuntimeException()

/** Hardware seam: reads are observations, never desired-state caches. Null means unavailable. */
internal interface DeviceControlPlatform {
    fun cameraPermission(): Boolean
    fun writeSettingsPermission(): Boolean
    fun setTorch(enabled: Boolean): Boolean?
    fun fixedVolume(): Boolean
    fun readVolume(stream: String): VolumeReading?
    fun setVolume(stream: String, index: Int)
    fun adjustVolume(stream: String, direction: String)
    fun writeSetting(key: String, value: Int): Boolean
    fun readSetting(key: String): Int?
    fun readBrightness(): BrightnessReading?
    /** Null means actually accepted; a queued tap route returns a restriction reason. */
    fun navigate(page: String?, packageName: String?): String?
    fun launchableApps(): List<LaunchableApp>
}

class NativeDeviceControls internal constructor(private val platform: DeviceControlPlatform) {
    constructor(context: Context) : this(AndroidDeviceControlPlatform(context))

    fun executeRaw(capability: String, argumentsJson: String): JSONObject? {
        if (!owns(capability)) return null
        return guarded { executeValidated(capability, StrictDeviceArguments.parse(argumentsJson)) }
    }

    fun execute(capability: String, args: JSONObject): JSONObject? {
        if (!owns(capability)) return null
        return guarded { executeValidated(capability, args) }
    }

    private fun guarded(block: () -> JSONObject): JSONObject = try { block() }
    catch (error: DeviceControlFailure) { failure(error.reason, error.status) }
    catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        failure("operation_rejected")
    }
    catch (_: SecurityException) { failure("permission_denied") }
    catch (_: Exception) { failure("operation_rejected") }

    private fun executeValidated(capability: String, args: JSONObject): JSONObject {
        // Validate all fields before any platform interaction.
        val fields = when (capability) {
            "android.flashlight_set", "android.rotation_set" -> setOf("enabled")
            "android.volume_get" -> setOf("stream")
            "android.volume_set" -> setOf("percentage", "stream")
            "android.volume_adjust" -> setOf("direction", "stream")
            "android.brightness_set" -> setOf("percentage")
            "android.screen_timeout_set" -> setOf("milliseconds")
            "android.settings_open" -> setOf("page")
            "android.app_open" -> setOf("package")
            else -> setOf("query")
        }
        require(args.keys().asSequence().toSet() == fields)
        val enabled = if ("enabled" in fields) args.get("enabled").also { require(it is Boolean) } as Boolean else false
        val stream = if ("stream" in fields) string(args, "stream").also { require(it in streams) } else "media"
        val percent = if ("percentage" in fields) integer(args, "percentage", 0, 100) else 0
        val direction = if ("direction" in fields) string(args, "direction").also { require(it in setOf("up", "down", "mute", "unmute")) } else ""
        val timeout = if ("milliseconds" in fields) integer(args, "milliseconds", 15000, 1800000) else 0
        val page = if ("page" in fields) string(args, "page").also { require(it in pages) } else null
        val packageName = if ("package" in fields) string(args, "package").also { require(validPackage(it)) } else null
        val query = if ("query" in fields) string(args, "query").also { require(it.toByteArray(Charsets.UTF_8).size <= 256) } else ""
        return when (capability) {
            "android.flashlight_set" -> {
                if (!platform.cameraPermission()) return failure("permission_denied")
                val observed = platform.setTorch(enabled)
                success(JSONObject().put("observed", observed?.let { JSONObject().put("enabled", it) } ?: JSONObject.NULL))
            }
            "android.volume_get" -> success(JSONObject().put("stream", stream).put("observed", platform.readVolume(stream)?.json() ?: JSONObject.NULL))
            "android.volume_set", "android.volume_adjust" -> {
                if (platform.fixedVolume()) return failure("fixed_volume", "unsupported")
                val before = platform.readVolume(stream)
                before?.json() // Check platform bounds before mutating.
                if (before != null && before.min == before.max) return failure("fixed_volume", "unsupported")
                try {
                    if (capability == "android.volume_set") {
                        // Percentage mapping requires real bounds; unavailable bounds cannot authorize a guessed write.
                        if (before == null) return failure("operation_rejected")
                        platform.setVolume(stream, volumeIndex(before.min, before.max, percent))
                    } else if (before != null && direction in setOf("up", "down")) {
                        val delta = if (direction == "up") 1L else -1L
                        val target = (before.index.toLong() + delta).coerceIn(before.min.toLong(), before.max.toLong()).toInt()
                        platform.setVolume(stream, target)
                    } else platform.adjustVolume(stream, direction)
                } catch (_: SecurityException) { return failure("special_access_required") }
                val details = JSONObject().put("stream", stream)
                if (capability == "android.volume_adjust") details.put("before", before?.json() ?: JSONObject.NULL)
                success(details.put("observed", platform.readVolume(stream)?.json() ?: JSONObject.NULL))
            }
            "android.brightness_set", "android.rotation_set", "android.screen_timeout_set" -> {
                if (!platform.writeSettingsPermission()) return failure("special_access_required")
                val accepted = when (capability) {
                    "android.brightness_set" -> platform.writeSetting("screen_brightness_mode", 0) && platform.writeSetting("screen_brightness", volumeIndex(0, 255, percent))
                    "android.rotation_set" -> platform.writeSetting("accelerometer_rotation", if (enabled) 1 else 0)
                    else -> platform.writeSetting("screen_off_timeout", timeout)
                }
                if (!accepted) return failure("policy_rejected")
                val observed = when (capability) {
                    "android.brightness_set" -> platform.readBrightness()?.let {
                        require(it.value in 0..255 && it.mode in setOf("manual", "automatic"))
                        JSONObject().put("value", it.value).put("mode", it.mode)
                    }
                    "android.rotation_set" -> platform.readSetting("accelerometer_rotation")?.let { require(it in 0..1)
                        JSONObject().put("enabled", it == 1) }
                    else -> platform.readSetting("screen_off_timeout")?.let { require(it > 0)
                        JSONObject().put("milliseconds", it) }
                }
                success(JSONObject().put("observed", observed ?: JSONObject.NULL))
            }
            "android.settings_open", "android.app_open" -> {
                val reason = platform.navigate(page, packageName)
                if (reason != null) failure(reason)
                else success(JSONObject().put(if (page != null) "page" else "package", page ?: packageName).put("accepted", true))
            }
            else -> inventory(query)
        }
    }

    private fun inventory(query: String): JSONObject {
        val apps = JSONArray()
        val details = JSONObject().put("query", query).put("apps", apps).put("truncated", false)
        val matching = platform.launchableApps().asSequence().filter { validPackage(it.packageName) }
            .distinctBy { it.packageName }.filter { it.packageName.contains(query, true) || it.label.contains(query, true) }
            .sortedBy { it.packageName }.toList()
        var truncated = false
        for (app in matching) {
            val cleaned = safeLabel(app.label, app.packageName)
            if (apps.length() == 128) {
                truncated = true
                break
            }
            val record = JSONObject().put("package", app.packageName).put("label", cleaned)
            apps.put(record)
            // Reserve true's shorter representation too; false is one byte longer.
            if (details.toString().toByteArray(Charsets.UTF_8).size > 32768) {
                apps.remove(apps.length() - 1)
                truncated = true
                break
            }
        }
        return success(details.put("truncated", truncated))
    }

    companion object {
        private val streams = setOf("media", "ring", "alarm", "notification", "system", "voice_call")
        internal val pages = setOf("wifi", "bluetooth", "display", "sound", "apps", "app_permissions", "notifications", "accessibility", "write_settings", "dnd")
        private val names = setOf("flashlight_set", "volume_get", "volume_set", "volume_adjust", "brightness_set", "rotation_set", "screen_timeout_set", "settings_open", "app_open", "app_list").map { "android.$it" }.toSet()
        fun owns(capability: String) = capability in names
        internal fun volumeIndex(min: Int, max: Int, percentage: Int) = min + (((max.toLong() - min) * percentage + 50) / 100).toInt()
        internal fun validPackage(value: String) = value.toByteArray(Charsets.UTF_8).size in 1..255 && Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value)
        private fun string(args: JSONObject, key: String): String = (args.get(key) as? String ?: throw IllegalArgumentException()).also { require(StrictDeviceArguments.validUnicode(it) && '\u0000' !in it) }
        private fun integer(args: JSONObject, key: String, min: Int, max: Int): Int {
            val value = args.get(key)
            require(value is Int || value is Long)
            val number = (value as Number).toLong()
            require(number in min.toLong()..max.toLong())
            return number.toInt()
        }
        private fun safeLabel(label: String, fallback: String): String {
            if (label.isEmpty() || '\u0000' in label || !StrictDeviceArguments.validUnicode(label)) return fallback
            return label.substring(0, label.offsetByCodePoints(0, minOf(256, label.codePointCount(0, label.length))))
        }
        internal fun failure(reason: String, status: String = "failure"): JSONObject {
            require(reason in setOf("permission_denied", "special_access_required", "hardware_unavailable", "camera_busy", "fixed_volume", "policy_rejected", "background_launch_restricted", "handler_unavailable", "operation_rejected"))
            return JSONObject().put("status", status).put("details", JSONObject().put("reason", reason))
        }
        private fun success(details: JSONObject) = JSONObject().put("status", "success").put("details", details)
    }
}

/** Closed flat JSON grammar, independent of Android's permissive JSONObject parser. */
internal object StrictDeviceArguments {
    fun validUnicode(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val character = value[index++]
            if (character.isHighSurrogate()) {
                if (index == value.length || !value[index++].isLowSurrogate()) return false
            } else if (character.isLowSurrogate()) return false
        }
        return true
    }

    fun parse(raw: String): JSONObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= 65536 && validUnicode(raw))
        return Parser(raw).parse()
    }

    private class Parser(private val raw: String) {
        private var position = 0

        private fun whitespace() {
            while (position < raw.length && raw[position] in " \t\r\n") position++
        }

        private fun take(character: Char) {
            whitespace()
            require(position < raw.length && raw[position++] == character)
        }

        fun parse(): JSONObject {
            val result = JSONObject()
            val keys = mutableSetOf<String>()
            take('{')
            whitespace()
            if (raw.getOrNull(position) == '}') {
                position++
            } else while (true) {
                val key = string()
                require(keys.add(key))
                take(':')
                whitespace()
                val value: Any = when (raw.getOrNull(position)) {
                    '"' -> string()
                    't' -> {
                        literal("true")
                        true
                    }
                    'f' -> {
                        literal("false")
                        false
                    }
                    else -> integer()
                }
                result.put(key, value)
                whitespace()
                if (raw.getOrNull(position) == '}') {
                    position++
                    break
                }
                take(',')
            }
            whitespace()
            require(position == raw.length)
            return result
        }

        private fun integer(): Long {
            val start = position
            if (raw.getOrNull(position) == '-') position++
            if (raw.getOrNull(position) == '0') {
                position++
            } else {
                require(raw.getOrNull(position) in '1'..'9')
                while (raw.getOrNull(position) in '0'..'9') position++
            }
            return raw.substring(start, position).toLong()
        }

        private fun literal(value: String) {
            require(raw.startsWith(value, position))
            position += value.length
        }

        private fun string(): String {
            take('"')
            val result = StringBuilder()
            while (true) {
                require(position < raw.length)
                val character = raw[position++]
                if (character == '"') break
                require(character.code >= 32)
                if (character != '\\') result.append(character) else result.append(escape())
            }
            return result.toString().also { require('\u0000' !in it && validUnicode(it)) }
        }

        private fun escape(): Char {
            require(position < raw.length)
            return when (val escaped = raw[position++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(position + 4 <= raw.length)
                    val hex = raw.substring(position, position + 4)
                    require(hex.all { it in "0123456789abcdefABCDEF" })
                    position += 4
                    hex.toInt(16).toChar()
                }
                else -> throw IllegalArgumentException()
            }
        }
    }
}
