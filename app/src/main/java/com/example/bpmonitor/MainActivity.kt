package com.example.bpmonitor

import android.content.Intent
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

    class Adapter(val onLong: (BpRecord) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<BpRecord> = emptyList()
        private val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvItem)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
            LayoutInflater.from(p.context).inflate(R.layout.item_record, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = items[pos]
            val p = if (r.period == "morning") "Утро" else "Вечер"
            h.tv.text = "${fmt.format(Date(r.timestamp))}  •  $p\n" +
                    "${r.systolic}/${r.diastolic} мм рт.ст.   пульс ${r.pulse}"
            h.itemView.setOnLongClickListener { onLong(r); true }
        }
        override fun getItemCount() = items.size
        fun update(l: List<BpRecord>) { items = l; notifyDataSetChanged() }
    }
}
