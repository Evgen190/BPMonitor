package com.example.bpmonitor

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
    private lateinit var calendarContainer: LinearLayout
    private lateinit var calendarScroll: ScrollView

    private var currentPeriod = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        chart = findViewById(R.id.chart)
        tvStats = findViewById(R.id.tvStats)
        calendarContainer = findViewById(R.id.calendarContainer)
        calendarScroll = findViewById(R.id.calendarScroll)

        val spinnerPeriod = findViewById<Spinner>(R.id.spinnerPeriod)
        spinnerPeriod.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Месяц", "Квартал", "Год"))
        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentPeriod = pos; load()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val spinnerView = findViewById<Spinner>(R.id.spinnerView)
        spinnerView.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("График", "Календарь"))
        spinnerView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val showChart = pos == 0
                chart.visibility = if (showChart) View.VISIBLE else View.GONE
                calendarScroll.visibility = if (showChart) View.GONE else View.VISIBLE
                load()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun periodRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis
        when (currentPeriod) {
            0 -> cal.add(Calendar.MONTH, -1)
            1 -> cal.add(Calendar.MONTH, -3)
            else -> cal.add(Calendar.YEAR, -1)
        }
        return Pair(cal.timeInMillis, to)
    }

    private fun load() {
        val (from, to) = periodRange()
        lifecycleScope.launch {
            val list = AppDatabase.get(this@ReportActivity).bpDao().getRange(from, to)
            showStats(list)
            if (chart.visibility == View.VISIBLE) drawChart(list)
            else drawCalendar(from, to, list)
        }
    }

    // ───────────────── График ─────────────────

    private fun drawChart(list: List<BpRecord>) {
        val pressure = list.filter { it.systolic > 0 && it.diastolic > 0 }
        if (pressure.isEmpty()) {
            chart.clear(); chart.invalidate(); return
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

        // Максимум по Y, чтобы рисовать метки сверху
        val maxY = pressure.maxOf { maxOf(it.systolic, it.pulse) } + 15f

        // Метки событий: индекс = позиция в pressure по времени
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

    // ───────────────── Календарь ─────────────────

    private fun drawCalendar(from: Long, to: Long, list: List<BpRecord>) {
        calendarContainer.removeAllViews()

        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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

        // Сетка
        val cur = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            val dow = get(Calendar.DAY_OF_WEEK)
            val offset = if (dow == Calendar.SUNDAY) -6 else -(dow - 2)
            add(Calendar.DAY_OF_MONTH, offset)
        }
        val dayFmt = SimpleDateFormat("d", Locale.getDefault())
        val endCal = Calendar.getInstance().apply { timeInMillis = to }

        while (cur.timeInMillis <= endCal.timeInMillis) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            for (i in 0 until 7) {
                val dayNum = dayFmt.format(cur.time)
                val key = keyFmt.format(cur.time)
                val m = dayMap[key]

                val cell = TextView(this).apply {
                    text = dayNum
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setTextColor(Color.parseColor("#3E2723"))
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    setPadding(4, 4, 4, 4)
                    // лёгкая рамка для всех
                    background = frameDrawable()
                }

                when {
                    m == null -> { /* без заливки */ }
                    m.a && m.h -> cell.background = splitDrawable()
                    m.a -> cell.background = solidDrawable("#FFB74D")
                    m.h -> cell.background = solidDrawable("#CE93D8")
                }

                row.addView(cell)
                cur.add(Calendar.DAY_OF_MONTH, 1)
            }
            calendarContainer.addView(row)
        }
    }

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

    // ───────────────── Статистика ─────────────────

    private fun showStats(list: List<BpRecord>) {
        val pressure = list.filter { it.systolic > 0 && it.diastolic > 0 }

        val sb = StringBuilder()
        sb.append("Записей всего: ${list.size}\n")

        if (pressure.isNotEmpty()) {
            sb.append("С давлением: ${pressure.size}\n")
            sb.append("Среднее: ${pressure.map { it.systolic }.average().toInt()}/" +
                      "${pressure.map { it.diastolic }.average().toInt()} мм рт.ст.\n")
            sb.append("Сист.: мин ${pressure.minOf { it.systolic }}, " +
                      "макс ${pressure.maxOf { it.systolic }}\n")
            sb.append("Диаст.: мин ${pressure.minOf { it.diastolic }}, " +
                      "макс ${pressure.maxOf { it.diastolic }}\n")
        } else {
            sb.append("Измерений давления за период нет\n")
        }

        // Дни событий (уникальные даты)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val alcoDays   = list.filter { it.alcohol }.map { dayFmt.format(Date(it.timestamp)) }.toSet()
        val hookahDays = list.filter { it.hookah  }.map { dayFmt.format(Date(it.timestamp)) }.toSet()
        val bothDays   = alcoDays intersect hookahDays

        sb.append("\nАлкоголь: ${alcoDays.size} дн.\n")
        sb.append("Кальян: ${hookahDays.size} дн.\n")
        sb.append("Оба в один день: ${bothDays.size} дн.")

        tvStats.text = sb.toString()
    }
}
