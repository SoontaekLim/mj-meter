package kr.neya.hometemperature

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View.OnTouchListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis.XAxisPosition
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.DefaultAxisValueFormatter
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import kr.neya.hometemperature.constants.Ids
import kr.neya.hometemperature.model.Temperature
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.collections.ArrayList

class ChartActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var mDetector: GestureDetectorCompat
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chart)

        mDetector = GestureDetectorCompat(this, DetectSwipeGestureListener(this))

        chart = findViewById(R.id.chart1)

        chart.xAxis.position = XAxisPosition.BOTTOM
        chart.setScaleEnabled(false)
        chart.isDragEnabled = false
        chart.xAxis.textColor = getColor(R.color.white)
        chart.axisLeft.textColor = getColor(R.color.white)
        chart.axisRight.textColor = getColor(R.color.white)
        val handleTouch = OnTouchListener { v, event ->
            mDetector.onTouchEvent(event)
            true
        }
        chart.setOnTouchListener(handleTouch)

        loadSheets()

//        val swipeContainer: SwipeRefreshLayout = findViewById(R.id.chartSwipeContainer)
//        swipeContainer.setOnRefreshListener {
//            loadSheets()
//            swipeContainer.isRefreshing = false
//        }
    }

    private fun loadSheets() {
        val secretKey = assets.open("credentials.json")

        val range = "Sheet1!A${2+(currentPage * 50)}:C${51+(currentPage * 50)}"
        Log.d("DEBUG", range)
        val sheetTask = ChartActivity.SheetTask(this, range)
        sheetTask.execute(secretKey)
    }

    private fun updateUI(values: List<Temperature>?) {
        val temperatureEntry = ArrayList<Entry>()
        val humidityEntry = ArrayList<Entry>()
        if (values != null) {
            val reversedValues = values.reversed()
            val xAxisValueFormatter = XAxisValueFormatter(0)
            xAxisValueFormatter.lastValues = reversedValues
            chart.xAxis.valueFormatter = xAxisValueFormatter

            for((index, value) in reversedValues.withIndex()) {
                if(index % 2 == 0) {
                    temperatureEntry.add(Entry(index.toFloat(), value.temperature.toFloat(), value.time))
                    humidityEntry.add(Entry(index.toFloat(), value.humidity.toFloat(), value.time))
                }
            }

            val temperatureSet = LineDataSet(temperatureEntry, "Temperature")
            temperatureSet.axisDependency = YAxis.AxisDependency.LEFT
            temperatureSet.color = getColor(R.color.temperature)
            temperatureSet.lineWidth = 4.0f

            temperatureSet.xMax
            val humiditySet = LineDataSet(humidityEntry, "Humidity")
            humiditySet.axisDependency = YAxis.AxisDependency.RIGHT
            humiditySet.color = getColor(R.color.humidity)
            humiditySet.lineWidth = 4.0f

            chart.data = LineData(temperatureSet, humiditySet)

            chart.legend.textColor = getColor(R.color.white)

            chart.axisLeft.axisMaximum = temperatureSet.yMax + 2
            chart.axisLeft.axisMinimum = temperatureSet.yMin - 2

            chart.axisRight.axisMaximum = humiditySet.yMax + 3
            chart.axisRight.axisMinimum = humiditySet.yMin - 3

            chart.xAxis.textColor

            chart.invalidate()
        }

    }

    private fun previousPage() {
        currentPage++
    }

    private fun nextPage() {
        if(currentPage > 0 ) {
            currentPage--
        }
    }

    private class SheetTask(val activity: ChartActivity, val range: String) :
        AsyncTask<InputStream, Void, List<Temperature>>() {
        private val IDX_DATE = 0
        private val IDX_TEMPERATURE = 1
        private val IDX_HUMIDITY = 2

        val APPLICATION_NAME = "Home Temperature"

        private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()

        private val SCOPES: List<String> =
            Arrays.asList("https://spreadsheets.google.com/feeds",
                "https://www.googleapis.com/auth/drive")

        override fun doInBackground(vararg params: InputStream?): List<Temperature> {
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
            val response = service.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues() ?: throw Exception("No data")

            val statusList = ArrayList<Temperature>()
            for (value in values) {
                statusList.add(
                    Temperature(value[IDX_DATE] as String,
                        BigDecimal((value[IDX_TEMPERATURE] as String).toDouble()).setScale(2, RoundingMode.HALF_EVEN).toDouble(),
                        BigDecimal((value[IDX_HUMIDITY] as String).toDouble()).setScale(2, RoundingMode.HALF_EVEN).toDouble()
                    )
                )
            }

            return statusList
        }

        override fun onPostExecute(result: List<Temperature>?) {
            super.onPostExecute(result)
            activity.updateUI(result)
        }

    }

    private class XAxisValueFormatter(digits: Int) : DefaultAxisValueFormatter(digits) {
        lateinit var lastValues: List<Temperature>

        override fun getAxisLabel(value: Float, axis: AxisBase): String {
            val time = lastValues[value.toInt()].time.split(" ").last()
            return time.slice(IntRange(0, time.lastIndexOf(":")-1))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (mDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    private class DetectSwipeGestureListener(val activity: ChartActivity) : GestureDetector.SimpleOnGestureListener() {
        // Minimal x and y axis swipe distance.
        private val MIN_SWIPE_DISTANCE_X = 100

        // Maximal x and y axis swipe distance.
        private val MAX_SWIPE_DISTANCE_X = 1000

        /* This method is invoked when a swipe gesture happened. */
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent?,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            Log.i("GestureListener", "onFling")
            println("onFling")
            if (e1 == null) {
                println("e1 is null")
                return false
            }
            if (e2 == null) {
                println("e2 is null")
                return false
            }
            // Get swipe delta value in x axis.
            val deltaX = e1.x - e2.x

            // Get swipe delta value in y axis.
            val deltaY = e1.y - e2.y

            // Get absolute value.
            val deltaXAbs = Math.abs(deltaX)
            val deltaYAbs = Math.abs(deltaY)

            // Only when swipe distance between minimal and maximal distance value then we treat it as effective swipe
            if (deltaXAbs >= MIN_SWIPE_DISTANCE_X && deltaXAbs <= MAX_SWIPE_DISTANCE_X) {
                if (deltaX > 0) {
                    Log.i("GestureListener", "Swipe to left")
                    activity.nextPage()
                } else {
                    Log.i("GestureListener", "Swipe to right")
                    activity.previousPage()

                }
                activity.loadSheets()
            }
            return true
        }
    }
}