package com.example.bpmonitor

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {
    private lateinit var chart: LineChart
    private lateinit var tvStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        chart = findViewById(R.id.chart)
        tvStats = findViewById(R.id.tvStats)
        val spinner = findViewById<Spinner>(R.id.spinner)
        spinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Месяц", "Квартал", "Год"))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = load(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun load(pos: Int) {
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis
        when (pos) {
            0 -> cal.add(Calendar.MONTH, -1)
            1 -> cal.add(Calendar.MONTH, -3)
            else -> cal.add(Calendar.YEAR, -1)
        }
        val from = cal.timeInMillis
        lifecycleScope.launch {
            val list = AppDatabase.get(this@ReportActivity).bpDao().getRange(from, to)
            drawChart(list); showStats(list)
        }
    }

    private fun drawChart(list: List<BpRecord>) {
        if (list.isEmpty()) { chart.clear(); chart.invalidate(); return }
        fun ds(entries: List<Entry>, label: String, color: Int) =
            LineDataSet(entries, label).apply {
                this.color = color; setCircleColor(color); lineWidth = 2f; circleRadius = 3f
            }
        val sys = list.mapIndexed { i, r -> Entry(i.toFloat(), r.systolic.toFloat()) }
        val dia = list.mapIndexed { i, r -> Entry(i.toFloat(), r.diastolic.toFloat()) }
        val pul = list.mapIndexed { i, r -> Entry(i.toFloat(), r.pulse.toFloat()) }

        chart.data = LineData(
            ds(sys, "Систолическое", Color.parseColor("#D32F2F")),
            ds(dia, "Диастолическое", Color.parseColor("#1976D2")),
            ds(pul, "Пульс", Color.parseColor("#388E3C"))
        )
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        val fmt = SimpleDateFormat("dd.MM", Locale.getDefault())
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in list.indices) fmt.format(Date(list[i].timestamp)) else ""
            }
        }
        chart.invalidate()
    }

    private fun showStats(list: List<BpRecord>) {
        if (list.isEmpty()) { tvStats.text = "Нет данных за период"; return }
        tvStats.text = """
            Записей: ${list.size}  (утро: ${list.count { it.period == "morning" }},
            вечер: ${list.count { it.period == "evening" }})
            Среднее: ${list.map { it.systolic }.average().toInt()}/${list.map { it.diastolic }.average().toInt()} мм рт.ст.
            Систолическое: мин ${list.minOf { it.systolic }}, макс ${list.maxOf { it.systolic }}
            Диастолическое: мин ${list.minOf { it.diastolic }}, макс ${list.maxOf { it.diastolic }}
        """.trimIndent()
    }
}
