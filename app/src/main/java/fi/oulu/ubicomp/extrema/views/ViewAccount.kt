package fi.oulu.ubicomp.extrema.views

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList

class ViewAccount : AppCompatActivity() {
    lateinit var db: ExtremaDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences(Home.EXTREMA_PREFS, 0)

        doAsync {
            val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                    .build()

            val participant = db.participantDao().getParticipant().first()

            uiThread {
                participantName.setText(participant.participantName)
                participantName.isEnabled = false

                participantId.setText(participant.participantId)
                participantId.isEnabled = false

                participantEmail.setText(participant.participantEmail)
                participantEmail.isEnabled = false

                val countries = getCountries()
                participantCountry.adapter = countries
                participantCountry.setSelection(countries.getPosition(participant.participantCountry), true)
                participantCountry.dispatchSetSelected(true)

                if (supportsBLE()) {
                    ruuviStatus.setText(participant.ruuviTag)
                } else {
                    ruuviStatus.visibility = View.INVISIBLE
                }

                btnSaveParticipant.text = getString(R.string.ok)
                btnSaveParticipant.setOnClickListener {
                    doAsync {
                        if (participant.ruuviTag != ruuviStatus.text.toString()) {
                            participant.uid = null
                            participant.ruuviTag = ruuviStatus.text.toString()
                            participant.timestamp = System.currentTimeMillis() //we create a new onboarding since the tag changed so it syncs
                            db.participantDao().insert(participant)
                            db.close()
                        }
                        db.close()
                    }
                    finish()
                }
            }
        }
    }

    fun supportsBLE() = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun getCountries(): ArrayAdapter<String> {
        val locales = Locale.getAvailableLocales()
        val countries = ArrayList<String>()
        for (country in locales) {
            if (!countries.contains(country.displayCountry) && country.displayCountry.isNotEmpty()) {
                countries.add(country.displayCountry)
            }
        }
        Collections.sort(countries, String.CASE_INSENSITIVE_ORDER)
        return ArrayAdapter(applicationContext, R.layout.country_spinner_item, countries)
    }
}
