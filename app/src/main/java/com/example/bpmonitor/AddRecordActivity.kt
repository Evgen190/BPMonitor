package com.example.bpmonitor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)

        val etInput  = findViewById<EditText>(R.id.etInput)
        val spPeriod = findViewById<Spinner>(R.id.spPeriod)
        val tvTime   = findViewById<TextView>(R.id.tvTime)
        val btnSave  = findViewById<MaterialButton>(R.id.btnSave)

        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        fun refreshTime() { tvTime.text = fmt.format(Date(timestamp)) }
        refreshTime()

        // Спиннер: Утро / Вечер. По умолчанию — по текущему часу
        spPeriod.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, listOf("Утро", "Вечер"))
        spPeriod.setSelection(
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12) 0 else 1)

        // Тап по времени — диалог даты и времени
        tvTime.setOnClickListener {
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

        btnSave.setOnClickListener { save(etInput, spPeriod) }

        // Enter на клавиатуре = сохранить
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { save(etInput, spPeriod); true }
            else false
        }
    }

    private fun save(etInput: EditText, spPeriod: Spinner) {
        val (sys, dia, pulse) = parse(etInput.text.toString())
        if (sys == null || dia == null) {
            Toast.makeText(this, "Формат: 120/80 или 120/80 70", Toast.LENGTH_SHORT).show()
            return
        }
        val period = if (spPeriod.selectedItemPosition == 0) "morning" else "evening"
        lifecycleScope.launch {
            AppDatabase.get(this@AddRecordActivity).bpDao().insert(
                BpRecord(systolic = sys, diastolic = dia, pulse = pulse ?: 0,
                    timestamp = timestamp, period = period))
            finish()
        }
    }

    /** Принимает "120/80", "120/80 70", "120 80 70", "120,80,70". */
    private fun parse(raw: String): Triple<Int?, Int?, Int?> {
        val cleaned = raw.replace(',', '/').trim()
        val parts = cleaned.split(Regex("[/\\s]+")).filter { it.isNotEmpty() }
        if (parts.size < 2) return Triple(null, null, null)
        return Triple(
            parts[0].toIntOrNull(),
            parts[1].toIntOrNull(),
            parts.getOrNull(2)?.toIntOrNull()
        )
    }
}
