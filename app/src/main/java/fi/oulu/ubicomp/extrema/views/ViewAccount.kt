package fi.oulu.ubicomp.extrema.views

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SpinnerAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList

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

                participantCountry.adapter = getCountries(participant.participantCountry)

                ruuviStatus.text = participant.ruuviTag

                btnSaveParticipant.text = getString(R.string.ok)
                btnSaveParticipant.setOnClickListener {
                    finish()
                }
            }
        }
        db.close()
    }

    fun getCountries(selected : String) : SpinnerAdapter {
        val locales = Locale.getAvailableLocales()
        val countries = ArrayList<String>()
        for (country in locales) {
            if (country.displayCountry.equals(selected, true)) {
                countries.add(country.displayCountry)
                break
            }
        }
        Collections.sort(countries, String.CASE_INSENSITIVE_ORDER)
        return ArrayAdapter(applicationContext, android.R.layout.simple_spinner_item, countries)
    }
}