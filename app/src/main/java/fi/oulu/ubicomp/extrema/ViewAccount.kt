package fi.oulu.ubicomp.extrema

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync

class ViewAccount : AppCompatActivity() {

    lateinit var db: ExtremaDatabase

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
                db.participantDao().insertAll(participant)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        doAsync {
            val participant = db.participantDao().getParticipant()
            Log.d("EXTREMA", participant.toString())
        }
    }
}
