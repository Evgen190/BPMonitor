package com.example.bpmonitor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddRecordActivity : AppCompatActivity() {
    private var timestamp = System.currentTimeMillis()
    private var alcoholOn = false
    private var hookahOn  = false

    private val colorOff     = Color.parseColor("#B0BEC5")
    private val colorAlcohol = Color.parseColor("#F57C00")
    private val colorHookah  = Color.parseColor("#7B1FA2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)

        val etInput     = findViewById<EditText>(R.id.etInput)
        val btnDateTime = findViewById<MaterialButton>(R.id.btnDateTime)
        val btnAlcohol  = findViewById<MaterialButton>(R.id.btnAlcohol)
        val btnHookah   = findViewById<MaterialButton>(R.id.btnHookah)
        val tvTime      = findViewById<TextView>(R.id.tvTime)
        val btnSave     = findViewById<MaterialButton>(R.id.btnSave)

        val fmt = SimpleDateFormat("dd.MM.yyyy   HH:mm", Locale.getDefault())
        fun refreshTime() { tvTime.text = fmt.format(Date(timestamp)) }
        refreshTime()

        btnDateTime.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                TimePickerDialog(this, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                    timestamp = cal.timeInMillis; refreshTime()
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
               cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnAlcohol.setOnClickListener {
            alcoholOn = !alcoholOn
            btnAlcohol.backgroundTintList =
                ColorStateList.valueOf(if (alcoholOn) colorAlcohol else colorOff)
        }
        btnHookah.setOnClickListener {
            hookahOn = !hookahOn
            btnHookah.backgroundTintList =
                ColorStateList.valueOf(if (hookahOn) colorHookah else colorOff)
        }

        btnSave.setOnClickListener { save(etInput) }

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { save(etInput); true } else false
        }
    }

    private fun save(etInput: EditText) {
        val (sys, dia, pulse) = parse(etInput.text.toString())
        val hasPressure = sys != null && dia != null

        if (!hasPressure && !alcoholOn && !hookahOn) {
            Toast.makeText(this,
                "Введите давление или отметьте событие (Алкоголь / Кальян)",
                Toast.LENGTH_SHORT).show()
            return
        }

        val hour = Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(Calendar.HOUR_OF_DAY)
        val period = if (hour < 12) "morning" else "evening"

        lifecycleScope.launch {
            AppDatabase.get(this@AddRecordActivity).bpDao().insert(
                BpRecord(
                    systolic  = sys ?: 0,
                    diastolic = dia ?: 0,
                    pulse     = pulse ?: 0,
                    timestamp = timestamp,
                    period    = period,
                    alcohol   = alcoholOn,
                    hookah    = hookahOn
                ))
            finish()
        }
    }

    private fun parse(raw: String): Triple<Int?, Int?, Int?> {
        val cleaned = raw.replace(',', ' ').replace('/', ' ').trim()
        val parts = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 2) return Triple(null, null, null)
        return Triple(
            parts[0].toIntOrNull(),
            parts[1].toIntOrNull(),
            parts.getOrNull(2)?.toIntOrNull()
        )
    }
}
