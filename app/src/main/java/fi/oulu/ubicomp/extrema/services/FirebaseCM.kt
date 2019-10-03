package fi.oulu.ubicomp.extrema.services

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fi.oulu.ubicomp.extrema.workers.LocationWorker

class FirebaseCM : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        println("Received FCM, checking location now")
        val locationUpdate = OneTimeWorkRequest.Builder(LocationWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(locationUpdate)
    }
}