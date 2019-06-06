package fi.oulu.ubicomp.extrema.views

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import fi.oulu.ubicomp.extrema.database.Survey
import kotlinx.android.synthetic.main.activity_view_survey.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.json.JSONObject

class ViewSurvey : AppCompatActivity() {

    lateinit var symptomShortness : RadioGroup
    lateinit var symptomCough : RadioGroup
    lateinit var symptomPhlegm : RadioGroup
    lateinit var symptomWheezing : RadioGroup
    lateinit var symptomFlue:RadioGroup
    lateinit var frequencyNocturnal : RadioGroup
    lateinit var frequencyOpenMeds : RadioGroup
    lateinit var estimationAsthma : RadioGroup
    lateinit var preventNormalLife : RadioGroup
    lateinit var otherObs : EditText

    lateinit var save : Button

    var surveyData : JSONObject = JSONObject()
    var db: ExtremaDatabase? = null
    var participantData: Participant? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_survey)

        doAsync {
            db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()
            participantData = db?.participantDao()?.getParticipant()

            welcomeHeader.text = "${resources.getString(R.string.welcome)} ${participantData?.participantName}"
        }
    }

    override fun onResume() {
        super.onResume()

        welcomeHeader.text = "${resources.getString(R.string.welcome)} ${participantData?.participantName}"

        symptomShortness = shortnessBreath
        symptomShortness.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("symptomShortness", group.indexOfChild(radioSelected) + 1)
        }

        symptomCough = cough
        symptomCough.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("symptomCough", group.indexOfChild(radioSelected) + 1)
        }

        symptomPhlegm = phlegm
        symptomPhlegm.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("symptomPhlegm", group.indexOfChild(radioSelected) + 1)
        }

        symptomWheezing = wheezing
        symptomWheezing.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("symptomWheezing", group.indexOfChild(radioSelected) + 1)
        }

        symptomFlue=flue
        symptomWheezing.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("symptomFlue", group.indexOfChild(radioSelected) + 1)
        }

        frequencyNocturnal = nocturnal
        frequencyNocturnal.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("frequencyNocturnal", group.indexOfChild(radioSelected) + 1)
        }

        frequencyOpenMeds = opening_meds
        frequencyOpenMeds.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("frequencyOpenMeds", group.indexOfChild(radioSelected) + 1)
        }

        estimationAsthma = estimation_asthma
        estimationAsthma.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("estimationAsthmaBalance", group.indexOfChild(radioSelected) + 1)
        }

        preventNormalLife = preventNormal
        preventNormalLife.setOnCheckedChangeListener { group, checkedId ->
            val radioSelected : RadioButton = group.findViewById(checkedId)
            surveyData.put("preventNormal", group.indexOfChild(radioSelected) + 1)
        }

        otherObs = otherConsiderations

        save = saveDiary
        save.setOnClickListener {
            surveyData.put("otherObs", otherObs.text.toString())

            doAsync {
                val survey = Survey(null,
                        participantId = participantData?.participantId,
                        entryDate = System.currentTimeMillis(),
                        surveyData = surveyData.toString())

                db?.surveyDao()?.insert(survey)
            }

            toast(getString(R.string.thanks))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        db?.close()
    }
}
