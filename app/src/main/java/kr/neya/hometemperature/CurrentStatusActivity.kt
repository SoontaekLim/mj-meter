package kr.neya.hometemperature

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import kr.neya.hometemperature.constants.Background
import kr.neya.hometemperature.constants.Ids
import kr.neya.hometemperature.model.Temperature
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*


class CurrentStatusActivity : AppCompatActivity(){

    private lateinit var mMainLayout: LinearLayout
    private lateinit var mTemperatureLayout: LinearLayout
    private lateinit var mLastDateTextView: TextView
    private lateinit var mTemperatureTextView: TextView
    private lateinit var mHumidityTextView: TextView

    private var currentBackground: Background = Background.WARM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Views
        mMainLayout = findViewById(R.id.main_layout)
        mTemperatureLayout = findViewById(R.id.temperature_layout)
        mLastDateTextView = findViewById(R.id.last_date)
        mTemperatureTextView = findViewById(R.id.temperature)
        mHumidityTextView = findViewById(R.id.humidity)

        loadSheets()

        val swipeContainer: SwipeRefreshLayout = findViewById(R.id.swipeContainer)
        swipeContainer.setOnRefreshListener {
            loadSheets()
            swipeContainer.isRefreshing = false
        }

    }


    private fun loadSheets() {
        val secretKey = assets.open("credentials.json")

        val sheetTask = SheetTask(this)
        sheetTask.execute(secretKey)
    }

    private fun updateUI(result: Temperature?) = if (result != null) {
        if(result.temperature > 23) {
            currentBackground = Background.WARM
            mTemperatureTextView.background = getDrawable(R.drawable.warm_circle)
        } else {
            currentBackground = Background.COOL
            mTemperatureTextView.background = getDrawable(R.drawable.cool_circle)
        }

        mLastDateTextView.text = getString(R.string.last_date, result.time)
        mTemperatureTextView.text = getString(R.string.temperature, result.temperature.toString())
        mHumidityTextView.text = getString(R.string.humidity, result.humidity.toString())

    } else {
        mLastDateTextView.text = getString(R.string.loading)
        mTemperatureTextView.text = getString(R.string.loading)
        mHumidityTextView.text = getString(R.string.loading)
    }

    private class SheetTask(val activity: CurrentStatusActivity) :
        AsyncTask<InputStream, Void, Temperature>() {
        private val IDX_DATE = 0
        private val IDX_TEMPERATURE = 1
        private val IDX_HUMIDITY = 2

        val APPLICATION_NAME = "Home Temperature"

        private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()

        private val SCOPES: List<String> =
            Arrays.asList("https://spreadsheets.google.com/feeds",
                "https://www.googleapis.com/auth/drive")

        override fun doInBackground(vararg params: InputStream?): Temperature {
            val HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport()

            val credentials = GoogleCredential.fromStream(params.get(0))
                .createScoped(SCOPES)

            val service =
                Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME)
                    .build()
            val spreadsheetId = Ids.spreadsheet
            val worksheet = service.spreadsheets().get(spreadsheetId)
            worksheet.values
            val range = "Sheet1!A2:C2"
            val response = service.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues() ?: throw Exception("No data")
            val lastValue = values[0]

            val lastTemperature: Double =
                BigDecimal((lastValue[IDX_TEMPERATURE] as String).toDouble()).setScale(2, RoundingMode.HALF_EVEN).toDouble()
            val humidity: Double =
                BigDecimal((lastValue[IDX_HUMIDITY] as String).toDouble()).setScale(2, RoundingMode.HALF_EVEN).toDouble()

            return Temperature(
                lastValue[IDX_DATE] as String,
                lastTemperature,
                humidity
            );
        }

        override fun onPostExecute(result: Temperature?) {
            super.onPostExecute(result)
            activity.updateUI(result)
        }

    }

    fun openChart(view: View) {
        mTemperatureLayout.background
        val intent = Intent(this, ChartActivity::class.java).apply {
            putExtra(Ids.background, currentBackground)
        }
        startActivity(intent)
    }

}
