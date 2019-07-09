package fi.oulu.ubicomp.extrema.views

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SpinnerAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
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

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()
        val prefs = getSharedPreferences(Home.EXTREMA_PREFS, 0)

        doAsync {
            val participant = db.participantDao().getParticipant()

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
                participantCountry.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        btnSaveParticipant.text = getString(R.string.update_title)
                    }

                    override fun onNothingSelected(p0: AdapterView<*>?) {
                    }
                }

                ruuviStatus.text = participant.ruuviTag

                btnSaveParticipant.text = getString(R.string.ok)
                btnSaveParticipant.setOnClickListener {
                    doAsync {
                        if (participant.participantCountry != participantCountry.selectedItem.toString()) {

                            participant.participantCountry = participantCountry.selectedItem.toString()
                            participant.uid = null
                            participant.onboardDate = System.currentTimeMillis()

                            db.participantDao().insert(participant)
                            db.close()

                            val jsonBuilder = GsonBuilder()
                            val jsonPost = jsonBuilder.create()
                            val requestQueue = Volley.newRequestQueue(applicationContext)

                            val data = JSONObject()
                                    .put("tableName", "participant")
                                    .put("deviceId", prefs.getString(Home.UUID, ""))
                                    .put("data", jsonPost.toJson(participant))
                                    .put("timestamp", System.currentTimeMillis())

                            val serverRequest = JsonObjectRequest(Request.Method.POST, Home.STUDY_URL, data,
                                    Response.Listener {
                                        println("OK $it")
                                    },
                                    Response.ErrorListener {
                                        if (it.networkResponse != null) {
                                            println("Error ${it.networkResponse.statusCode}")
                                        }
                                    }
                            )
                            requestQueue.add(serverRequest)
                        }
                    }
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (db.isOpen) db.close()
    }

    fun getCountries(): ArrayAdapter<String> {
        val locales = Locale.getAvailableLocales()
        val countries = ArrayList<String>()
        for (country in locales) {
            if (!countries.contains(country.displayCountry) && country.displayCountry.isNotEmpty()) {
                countries.add(country.displayCountry)
            }
        }
        Collections.sort(countries, String.CASE_INSENSITIVE_ORDER)
        return ArrayAdapter(applicationContext, android.R.layout.simple_spinner_item, countries)
    }
}