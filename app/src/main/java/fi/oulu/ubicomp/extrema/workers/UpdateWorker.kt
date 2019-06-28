package fi.oulu.ubicomp.extrema.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import fi.oulu.ubicomp.extrema.BuildConfig
import org.json.JSONObject

class UpdateWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val requestQueue = Volley.newRequestQueue(applicationContext)
        val serverRequest = object : JsonObjectRequest(Method.GET, "http://jenkins.awareframework.com/job/extrema/api/json", null,
                Response.Listener<JSONObject> {
                    if (it.getJSONObject("lastSuccessfulBuild").getInt("number") != BuildConfig.VERSION_CODE) {

                    }
                },
                Response.ErrorListener {

                }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json"
                return params
            }
        }
        requestQueue.add(serverRequest)

        return Result.success()
    }
}