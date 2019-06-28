package fi.oulu.ubicomp.extrema.views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class ViewAccount : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
    }

    override fun onResume() {
        super.onResume()

        val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()
        doAsync {
            val participant = db.participantDao().getParticipant()
            uiThread {
                participantName.setText(participant.participantName)
                participantId.setText(participant.participantId)
                participantEmail.setText(participant.participantEmail)
                btnSaveParticipant.text = getString(R.string.ok)
                btnSaveParticipant.setOnClickListener {
                    finish()
                }
            }
        }
        db.close()
    }
}