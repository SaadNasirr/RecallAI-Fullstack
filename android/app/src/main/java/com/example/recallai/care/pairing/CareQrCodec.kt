package com.example.recallai.care.pairing

import android.util.Log
import org.json.JSONObject

/**
 * Patient QR encodes a compact JSON invite so scanners get version + token reliably.
 * Caregiver scan path still accepts legacy plain-token QR codes.
 */
internal object CareQrCodec {
    private const val TAG = "CarePairingQR"
    private const val VERSION = 1

    /** Single-line JSON: `{"v":1,"t":"<token>"}` — no extra whitespace beyond JSONObject defaults. */
    fun encodeInvitePayload(token: String): String {
        val clean = token.trim()
        return JSONObject()
            .put("v", VERSION)
            .put("t", clean)
            .toString()
    }

    fun decodeInviteToken(raw: String): String {
        val s = raw.trim()
        if (!s.startsWith("{")) return s
        return try {
            val o = JSONObject(s)
            o.optString("t", "").ifBlank { o.optString("token", "") }.trim()
        } catch (e: Exception) {
            Log.w(TAG, "QR JSON parse failed; falling back to trimmed raw string", e)
            s
        }
    }

    fun logDecodedQr(raw: String) {
        val preview = if (raw.length <= 240) raw else raw.take(240) + "…"
        Log.d(TAG, "Decoded QR (${raw.length} chars): $preview")
    }
}
