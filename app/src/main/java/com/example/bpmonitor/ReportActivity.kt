package com.example.bpmonitor

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var tvStats: TextView
    private lateinit var tvMonthTitle: TextView
    private lateinit var calendarContainer: LinearLayout
    private lateinit var calendarScroll: ScrollView

    private var showChart = true

    /** Первое число текущего отображаемого месяца (в 00:00). */
    private val currentMonth: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private lateinit var gestureDetector: GestureDetector

    private val monthFmt = SimpleDateFormat("LLLL yyyy", Locale("ru"))
    private val dayNumFmt = SimpleDateFormat("d", Locale.getDefault())
    private val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        chart = findViewById(R.id.chart)
        tvStats = findViewById(R.id.tvStats)
        tvMonthTitle = findViewById(R.id.tvMonthTitle)
        calendarContainer = findViewById(R.id.calendarContainer)
        calendarScroll = findViewById(R.id.calendarScroll)

        // Переключатель График / Календарь
        val spinnerView = findViewById<Spinner>(R.id.spinnerView)
        spinnerView.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("График", "Календарь"))
        spinnerView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                showChart = pos == 0
                chart.visibility = if (showChart) View.VISIBLE else View.GONE
                calendarScroll.visibility = if (showChart) View.GONE else View.VISIBLE
                load()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Жесты свайпа
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 80f) {
                    if (dx < 0) nextMonth() else prevMonth()
                    return true
                }
                return false
            }
        })

        updateMonthTitle()
        load()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestureDetector.isInitialized) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun nextMonth() {
        currentMonth.add(Calendar.MONTH, 1)
        updateMonthTitle()
        load()
    }

    private fun prevMonth() {
        currentMonth.add(Calendar.MONTH, -1)
        updateMonthTitle()
        load()
    }

    private fun updateMonthTitle() {
        val raw = monthFmt.format(currentMonth.time)   // "октябрь 2026"
        tvMonthTitle.text = raw.replaceFirstChar { it.uppercase() }
    }

    private fun periodRange(): Pair<Long, Long> {
        val from = currentMonth.timeInMillis
        val endCal = (currentMonth.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return Pair(from, endCal.timeInMillis)
    }

    private fun load() {
        val (from, to) = periodRange()
        lifecycleScope.launch {
            val list = AppDatabase.get(this@ReportActivity).bpDao().getRange(from, to)
            showStats(list)
            if (showChart) drawChart(list) else drawCalendar(list)
        }
    }

    // ───────────── График ─────────────

    private fun drawChart(list: List<BpRecord>) {
        val pressure = list.filter { it.systolic > 0 && it.diastolic > 0 }
        if (pressure.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val sys = pressure.mapIndexed { i, r -> Entry(i.toFloat(), r.systolic.toFloat()) }
        val dia = pressure.mapIndexed { i, r -> Entry(i.toFloat(), r.diastolic.toFloat()) }
        val pul = pressure.mapIndexed { i, r -> Entry(i.toFloat(), r.pulse.toFloat()) }

        fun lineDs(entries: List<Entry>, label: String, color: Int) =
            LineDataSet(entries, label).apply {
                this.color = color; setCircleColor(color); lineWidth = 2f; circleRadius = 3f
            }

        val dataSets = mutableListOf<ILineDataSet>(
            lineDs(sys, "Систолическое", Color.parseColor("#D32F2F")),
            lineDs(dia, "Диастолическое", Color.parseColor("#1976D2")),
            lineDs(pul, "Пульс", Color.parseColor("#388E3C"))
        )

        val maxY = pressure.maxOf { maxOf(it.systolic, it.pulse) } + 15f

        val alcoholEntries = ArrayList<Entry>()
        val hookahEntries  = ArrayList<Entry>()
        pressure.forEachIndexed { i, r ->
            if (r.alcohol) alcoholEntries.add(Entry(i.toFloat(), maxY))
            if (r.hookah)  hookahEntries.add(Entry(i.toFloat(), maxY))
        }
        fun markerDs(entries: List<Entry>, label: String, color: Int) =
            LineDataSet(entries, label).apply {
                this.color = color
                setCircleColor(color)
                lineWidth = 0f
                circleRadius = 6f
                setDrawValues(false)
                setDrawFilled(false)
            }
        if (alcoholEntries.isNotEmpty())
            dataSets.add(markerDs(alcoholEntries, "Алкоголь", Color.parseColor("#F57C00")))
        if (hookahEntries.isNotEmpty())
            dataSets.add(markerDs(hookahEntries, "Кальян", Color.parseColor("#7B1FA2")))

        chart.data = LineData(dataSets)
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        val fmt = SimpleDateFormat("dd.MM", Locale.getDefault())
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in pressure.indices) fmt.format(Date(pressure[i].timestamp)) else ""
            }
        }
        chart.invalidate()
    }

    // ───────────── Календарь одного месяца ─────────────

    private fun drawCalendar(list: List<BpRecord>) {
        calendarContainer.removeAllViews()

        data class Marks(var a: Boolean = false, var h: Boolean = false)
        val dayMap = HashMap<String, Marks>()
        for (r in list) {
            val k = keyFmt.format(Date(r.timestamp))
            val m = dayMap.getOrPut(k) { Marks() }
            if (r.alcohol) m.a = true
            if (r.hookah)  m.h = true
        }

        // Заголовки дней недели
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach { name ->
            val tv = TextView(this).apply {
                text = name
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#6D4C41"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, 8, 0, 8)
            }
            headerRow.addView(tv)
        }
        calendarContainer.addView(headerRow)

        // Первая ячейка сетки — понедельник недели, в которую попадает 1-е число месяца
        val cellCal = (currentMonth.clone() as Calendar).apply {
            val dow = get(Calendar.DAY_OF_WEEK)             // 1 = Sun..7 = Sat
            val offset = if (dow == Calendar.SUNDAY) -6 else -(dow - 2)
            add(Calendar.DAY_OF_MONTH, offset)
        }

        // Последний день месяца — для остановки
        val endCal = (currentMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        while (cellCal.timeInMillis <= endCal.timeInMillis) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            for (i in 0 until 7) {
                val inMonth = cellCal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
                val dayNum = dayNumFmt.format(cellCal.time)
                val key = keyFmt.format(cellCal.time)
                val m = dayMap[key]

                val cell = TextView(this).apply {
                    text = dayNum
                    gravity = Gravity.CENTER
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    setPadding(4, 4, 4, 4)
                    setTextColor(if (inMonth) Color.parseColor("#3E2723")
                                 else Color.parseColor("#BDBDBD"))
                    background = frameDrawable()
                }

                if (inMonth) {
                    when {
                        m == null -> {}
                        m.a && m.h -> cell.background = splitDrawable()
                        m.a -> cell.background = solidDrawable("#FFB74D")
                        m.h -> cell.background = solidDrawable("#CE93D8")
                    }
                }

                row.addView(cell)
                cellCal.add(Calendar.DAY_OF_MONTH, 1)
            }
            calendarContainer.addView(row)
        }
    }

    // ───────────── Статистика ─────────────

    private fun showStats(list: List<BpRecord>) {
        val pressure = list.filter { it.systolic > 0 && it.diastolic > 0 }
        val sb = StringBuilder()
        sb.append("Записей: ${list.size}")
        if (pressure.isNotEmpty()) {
            sb.append("  (с давлением: ${pressure.size})\n")
            sb.append("Среднее: ${pressure.map { it.systolic }.average().toInt()}/" +
                      "${pressure.map { it.diastolic }.average().toInt()} мм рт.ст.\n")
            sb.append("Сист.: ${pressure.minOf { it.systolic }}–${pressure.maxOf { it.systolic }}   ")
            sb.append("Диаст.: ${pressure.minOf { it.diastolic }}–${pressure.maxOf { it.diastolic }}")
        } else {
            sb.append("\nИзмерений давления за месяц нет")
        }
        val alcoDays   = list.filter { it.alcohol }.map { keyFmt.format(Date(it.timestamp)) }.toSet()
        val hookahDays = list.filter { it.hookah  }.map { keyFmt.format(Date(it.timestamp)) }.toSet()
        val bothDays   = alcoDays intersect hookahDays
        sb.append("\nАлкоголь: ${alcoDays.size} дн.   ")
        sb.append("Кальян: ${hookahDays.size} дн.   ")
        sb.append("Оба: ${bothDays.size} дн.")
        tvStats.text = sb.toString()
    }

    // ───────────── Вспомогательные ─────────────

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun frameDrawable(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setStroke(dp(1), Color.parseColor("#D7CCC8"))
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(6).toFloat()
        }

    private fun solidDrawable(color: String): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(6).toFloat()
        }

    private fun splitDrawable(): android.graphics.drawable.Drawable {
        val gd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#FFB74D"), Color.parseColor("#CE93D8"))
        )
        gd.cornerRadius = dp(6).toFloat()
        return gd
    }
}
