package com.example.bpmonitor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
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

        val etSys = findViewById<EditText>(R.id.etSys)
        val etDia = findViewById<EditText>(R.id.etDia)
        val etPulse = findViewById<EditText>(R.id.etPulse)
        val tvTime = findViewById<TextView>(R.id.tvTime)
        val rgPeriod = findViewById<RadioGroup>(R.id.rgPeriod)
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        fun refresh() { tvTime.text = fmt.format(Date(timestamp)) }
        refresh()

        tvTime.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                TimePickerDialog(this, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                    timestamp = cal.timeInMillis; refresh()
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
               cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val sys = etSys.text.toString().toIntOrNull()
            val dia = etDia.text.toString().toIntOrNull()
            val pulse = etPulse.text.toString().toIntOrNull() ?: 0
            if (sys == null || dia == null) {
                Toast.makeText(this, "Введите показатели", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val period = if (rgPeriod.checkedRadioButtonId == R.id.rbMorning) "morning" else "evening"
            lifecycleScope.launch {
                AppDatabase.get(this@AddRecordActivity).bpDao().insert(
                    BpRecord(systolic = sys, diastolic = dia, pulse = pulse,
                        timestamp = timestamp, period = period))
                finish()
            }
        }
    }
}
