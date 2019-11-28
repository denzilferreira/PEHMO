package fi.oulu.ubicomp.extrema.views

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import kotlinx.android.synthetic.main.activity_account.*
import org.altbeacon.beacon.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONObject
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
                    .addMigrations(Home.MIGRATION_1_2, Home.MIGRATION_2_3)
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
                            participant.onboardDate = System.currentTimeMillis() //we create a new onboarding since the tag changed so it syncs

                            db.participantDao().insert(participant)

                            val requestQueue = Volley.newRequestQueue(applicationContext)

                            val createParticipant = object : StringRequest(Method.POST, "${Home.STUDY_URL}/participant/create_table", null,
                                    Response.ErrorListener {
                                        if (it.networkResponse == null) {
                                            println("Response null [participant create table]")
                                            println("Error: ${it.message}")
                                        }
                                    }) {}
                            requestQueue.add(createParticipant)

                            val serverRequest = object : StringRequest(Method.POST, "${Home.STUDY_URL}/participant/insert",
                                    Response.Listener {
                                        println("Sync OK [participant]")
                                        prefs.edit().putLong("participant", participant.onboardDate).apply()
                                    },
                                    Response.ErrorListener {
                                        if (it.networkResponse == null) {
                                            println("Response null [participant]")
                                            println("Error: ${it.message}")
                                        }
                                    }
                            ) {
                                override fun getParams(): MutableMap<String, String> {
                                    val params = HashMap<String, String>()
                                    params.put("device_id", prefs.getString(Home.UUID, "")!!)
                                    params.put("data", GsonBuilder().create().toJson(listOf(participant)))
                                    return params
                                }
                            }
                            requestQueue.add(serverRequest)
                        }
                    }

                    finish()
                }
            }

            db.close()
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
