package com.example.recallai.reminders

import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.CareAlertDispatchBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal object PatientAlarmCareNotifier {

    fun notifyMissedOrDismissed(title: String, body: String) {
        runBlocking(Dispatchers.IO) {
            runCatching {
                ApiClient.api.careAlertDispatch(
                    CareAlertDispatchBody(
                        type = "patient_alarm",
                        title = title,
                        body = body,
                        metadata = mapOf("source" to "patient_alarm")
                    )
                )
            }
        }
    }
}
