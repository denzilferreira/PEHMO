package fi.oulu.ubicomp.extrema

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync

class Home : AppCompatActivity() {

    companion object {
        val TAG = "EXTREMA"
        var participant : Participant ?= null
        var db: ExtremaDatabase ?= null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_account)

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()

        btnSaveParticipant.setOnClickListener {
            val participant = Participant(null,
                    participantBirth = "${participantDoB.year}/${participantDoB.month}/${participantDoB.dayOfMonth}",
                    participantEmail = participantEmail.text.toString(),
                    participantName = participantName.text.toString(),
                    participantId = participantId.text.toString(),
                    onboardDate = System.currentTimeMillis()
            )

            doAsync {
                db!!.participantDao().insert(participant)
                Companion.participant = db!!.participantDao().getParticipant()
                if (Companion.participant?.participantName!!.isNotEmpty()) {
                    startActivity(Intent(applicationContext, ViewSurvey::class.java))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        doAsync {
            participant = db?.participantDao()?.getParticipant()
            if (participant!!.participantName.isNotEmpty()) {
                startActivity(Intent(applicationContext, ViewSurvey::class.java))
            }
        }
    }
}
