package com.example.bpmonitor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dao = AppDatabase.get(this).bpDao()
        adapter = Adapter { rec -> lifecycleScope.launch { dao.delete(rec) } }

        findViewById<RecyclerView>(R.id.rv).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(Intent(this, AddRecordActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        lifecycleScope.launch {
            dao.getAllFlow().collect { list ->
                adapter.update(list)
                findViewById<TextView>(R.id.tvEmpty).visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    class Adapter(val onLong: (BpRecord) -> Unit) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        // Тип строки в списке: заголовок дня или само измерение
        sealed class Row {
            data class Header(val title: String) : Row()
            data class Item(val record: BpRecord) : Row()
        }

        private var rows: List<Row> = emptyList()

        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dayFmt  = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("ru"))

        // Цвета по уровню давления
        private val colorLow    = Color.parseColor("#1976D2") // синий
        private val colorNormal = Color.parseColor("#2E7D32") // зелёный
        private val colorHigh   = Color.parseColor("#C62828") // красный

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_ITEM   = 1
        }

        override fun getItemViewType(position: Int) =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(inflater.inflate(R.layout.item_day_header, parent, false))
            } else {
                ItemVH(inflater.inflate(R.layout.item_record, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderVH).tvDay.text = row.title
                is Row.Item   -> bindItem(holder as ItemVH, row.record)
            }
        }

        private fun bindItem(h: ItemVH, r: BpRecord) {
            val color = colorFor(r)
            val periodLabel = if (r.period == "morning") "У" else "В"
            val pulse = if (r.pulse > 0) "   п${r.pulse}" else ""
            h.tv.text = String.format(
                Locale.getDefault(),
                "%s  %s  %3d/%2d%s",
                timeFmt.format(Date(r.timestamp)), periodLabel,
                r.systolic, r.diastolic, pulse)
            h.tv.setTextColor(color)
            h.bar.setBackgroundColor(color)
            h.itemView.setOnLongClickListener { onLong(r); true }
        }

        private fun colorFor(r: BpRecord): Int = when {
            r.systolic < 100 || r.diastolic < 60 -> colorLow
            r.systolic >= 135 || r.diastolic >= 85 -> colorHigh
            else -> colorNormal
        }

        override fun getItemCount() = rows.size

        fun update(records: List<BpRecord>) {
            val out = ArrayList<Row>(records.size + 8)
            var lastDay: String? = null
            for (r in records) {           // уже отсортированы по timestamp DESC
                val day = dayFmt.format(Date(r.timestamp))
                if (day != lastDay) {
                    out.add(Row.Header(day))
                    lastDay = day
                }
                out.add(Row.Item(r))
            }
            rows = out
            notifyDataSetChanged()
        }

        class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val tvDay: TextView = v.findViewById(R.id.tvDay)
        }
        class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvItem)
            val bar: View = v.findViewById(R.id.colorBar)
        }
    }
}
