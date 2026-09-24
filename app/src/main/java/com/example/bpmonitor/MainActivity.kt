package com.example.bpmonitor

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
            addItemDecoration(StickyHeaderDecoration(this@MainActivity.adapter, this@MainActivity))
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

        sealed class Row {
            data class Header(val title: String) : Row()
            data class Item(val record: BpRecord) : Row()
        }

        private var rows: List<Row> = emptyList()
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dayFmt  = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("ru"))

        private val colorLow    = Color.parseColor("#1976D2")
        private val colorNormal = Color.parseColor("#2E7D32")
        private val colorHigh   = Color.parseColor("#C62828")

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
            val hasPressure = r.systolic > 0 && r.diastolic > 0
            val sb = StringBuilder(timeFmt.format(Date(r.timestamp)))
            if (hasPressure) {
                sb.append(String.format(Locale.getDefault(),
                    "   %3d/%2d", r.systolic, r.diastolic))
                if (r.pulse > 0) sb.append("   п${r.pulse}")
            } else {
                sb.append("   —")
            }
            h.tv.text = sb.toString()
            h.tv.setTextColor(if (hasPressure) colorFor(r) else Color.parseColor("#9E9E9E"))
            h.bar.setBackgroundColor(if (hasPressure) colorFor(r) else Color.parseColor("#BDBDBD"))

            h.tvAlcohol.visibility = if (r.alcohol) View.VISIBLE else View.GONE
            h.tvHookah.visibility  = if (r.hookah)  View.VISIBLE else View.GONE

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
            for (r in records) {
                val day = dayFmt.format(Date(r.timestamp))
                if (day != lastDay) { out.add(Row.Header(day)); lastDay = day }
                out.add(Row.Item(r))
            }
            rows = out
            notifyDataSetChanged()
        }

        fun getHeaderTitle(position: Int): String? =
            (rows.getOrNull(position) as? Row.Header)?.title

        class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val tvDay: TextView = v.findViewById(R.id.tvDay)
        }
        class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvItem)
            val bar: View = v.findViewById(R.id.colorBar)
            val tvAlcohol: TextView = v.findViewById(R.id.tvAlcohol)
            val tvHookah: TextView  = v.findViewById(R.id.tvHookah)
        }
    }

    class StickyHeaderDecoration(
        private val adapter: Adapter,
        context: Context
    ) : RecyclerView.ItemDecoration() {

        private val density = context.resources.displayMetrics.density
        private val headerHeight = 26f * density

        private val bgPaint = Paint().apply { color = Color.parseColor("#EFE4D0") }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6D4C41")
            textSize = 12f * context.resources.displayMetrics.scaledDensity
            typeface = Typeface.DEFAULT_BOLD
        }
        private val padding = 8f * density

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val topChild = parent.getChildAt(0) ?: return
            val topPos = parent.getChildAdapterPosition(topChild)
            if (topPos == RecyclerView.NO_POSITION) return

            var headerPos = topPos
            while (headerPos >= 0 && adapter.getItemViewType(headerPos) != Adapter.TYPE_HEADER) headerPos--
            if (headerPos < 0) return
            val title = adapter.getHeaderTitle(headerPos) ?: return

            var yOffset = 0f
            var nextHeaderPos = headerPos + 1
            while (nextHeaderPos < adapter.itemCount &&
                   adapter.getItemViewType(nextHeaderPos) != Adapter.TYPE_HEADER) nextHeaderPos++
            if (nextHeaderPos < adapter.itemCount) {
                val nextView = parent.findViewHolderForAdapterPosition(nextHeaderPos)?.itemView
                if (nextView != null && nextView.top < headerHeight) yOffset = nextView.top - headerHeight
            }

            c.save()
            c.translate(0f, yOffset)
            c.drawRect(0f, 0f, parent.width.toFloat(), headerHeight, bgPaint)
            val baseline = headerHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            c.drawText(title, padding, baseline, textPaint)
            c.restore()
        }
    }
}
