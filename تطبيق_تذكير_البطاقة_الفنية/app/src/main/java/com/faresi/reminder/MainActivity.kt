package com.faresi.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var list: MutableList<Reminder> = mutableListOf()
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var adapter: ArrayAdapter<String>

    private val dayNames = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val repeatNames = arrayOf("مرة واحدة", "يومي", "أسبوعي", "شهري")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "تذكير البطاقة الفنية"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(title)

        val addBtn = Button(this).apply {
            text = "إضافة تذكير"
            textSize = 18f
            setOnClickListener { showEditor(null) }
        }
        root.addView(addBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        emptyView = TextView(this).apply {
            text = "لا توجد تذكيرات بعد.\nاضغط «إضافة تذكير»."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(32), 0, 0)
        }
        root.addView(emptyView)

        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.textSize = 17f
                v.setPadding(dp(8), dp(14), dp(8), dp(14))
                v.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                return v
            }
        }
        listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> showItemMenu(list[pos]) }
        }
        root.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("إذن المنبهات الدقيقة")
                    .setMessage("لكي يرنّ التذكير في وقته بالضبط، فعّل «المنبهات والتذكيرات» لهذا التطبيق في الصفحة التالية.")
                    .setPositiveButton("فتح الإعدادات") { _, _ ->
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                        )
                    }
                    .setNegativeButton("لاحقاً", null)
                    .show()
            }
        }
    }

    private fun refresh() {
        list = Store.load(this)
        adapter.clear()
        adapter.addAll(list.map { describe(it) })
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun describe(r: Reminder): String {
        val time = String.format(Locale.US, "%02d:%02d", r.hour, r.minute)
        val rep = when (r.repeat) {
            0 -> "مرة واحدة: " + String.format(Locale.US, "%04d/%02d/%02d", r.year, r.month + 1, r.day)
            1 -> "يومياً"
            2 -> "كل " + (0..6).filter { ((r.days shr it) and 1) == 1 }.joinToString("، ") { dayNames[it] }
            else -> "شهرياً (يوم ${r.day})"
        }
        val state = if (r.active) "" else "  — متوقف"
        return "${r.title}\n$time  •  $rep$state"
    }

    private fun showItemMenu(r: Reminder) {
        val toggle = if (r.active) "إيقاف" else "تفعيل"
        AlertDialog.Builder(this)
            .setTitle(r.title)
            .setItems(arrayOf("تعديل", toggle, "حذف")) { _, which ->
                when (which) {
                    0 -> showEditor(r)
                    1 -> {
                        if (!r.active && Scheduler.nextTrigger(r.copy(active = true)) == null) {
                            Toast.makeText(this, "الموعد انتهى، عدّل التذكير أولاً", Toast.LENGTH_LONG).show()
                        } else {
                            r.active = !r.active
                            Store.save(this, list)
                            Scheduler.schedule(this, r)
                            refresh()
                        }
                    }
                    2 -> {
                        Scheduler.cancel(this, r.id)
                        list.removeAll { it.id == r.id }
                        Store.save(this, list)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun showEditor(existing: Reminder?) {
        val now = Calendar.getInstance()
        var y = existing?.year ?: now.get(Calendar.YEAR)
        var mo = existing?.month ?: now.get(Calendar.MONTH)
        var d = existing?.day ?: now.get(Calendar.DAY_OF_MONTH)
        var h = existing?.hour ?: 8
        var mi = existing?.minute ?: 0

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }

        val titleEt = EditText(this).apply {
            hint = "العنوان"
            setText(existing?.title ?: "إعداد البطاقة الفنية")
        }
        box.addView(titleEt)

        val dateBtn = Button(this)
        val timeBtn = Button(this)
        fun updateButtons() {
            dateBtn.text = "التاريخ: " + String.format(Locale.US, "%04d/%02d/%02d", y, mo + 1, d)
            timeBtn.text = "الساعة: " + String.format(Locale.US, "%02d:%02d", h, mi)
        }
        updateButtons()
        dateBtn.setOnClickListener {
            DatePickerDialog(this, { _, yy, mm, dd -> y = yy; mo = mm; d = dd; updateButtons() }, y, mo, d).show()
        }
        timeBtn.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm -> h = hh; mi = mm; updateButtons() }, h, mi, true).show()
        }
        box.addView(dateBtn)
        box.addView(timeBtn)

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repeatNames)
        box.addView(spinner)

        val daysBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val checks = dayNames.mapIndexed { i, name ->
            CheckBox(this).apply {
                text = name
                isChecked = existing != null && ((existing.days shr i) and 1) == 1
            }.also { daysBox.addView(it) }
        }
        box.addView(daysBox)

        fun updateVisibility(rep: Int) {
            dateBtn.visibility = if (rep == 0 || rep == 3) View.VISIBLE else View.GONE
            daysBox.visibility = if (rep == 2) View.VISIBLE else View.GONE
        }
        spinner.setSelection(existing?.repeat ?: 0)
        updateVisibility(existing?.repeat ?: 0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updateVisibility(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val noteEt = EditText(this).apply {
            hint = "ملاحظة (اختياري)"
            setText(existing?.note ?: "")
        }
        box.addView(noteEt)

        val scroll = ScrollView(this).apply { addView(box) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "تذكير جديد" else "تعديل التذكير")
            .setView(scroll)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val rep = spinner.selectedItemPosition
            var mask = 0
            checks.forEachIndexed { i, cb -> if (cb.isChecked) mask = mask or (1 shl i) }
            if (rep == 2 && mask == 0) {
                Toast.makeText(this, "اختر يوماً واحداً على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val title = titleEt.text.toString().trim().ifEmpty { "إعداد البطاقة الفنية" }
            val nr = Reminder(
                existing?.id ?: Store.nextId(list), title, noteEt.text.toString().trim(),
                y, mo, d, h, mi, rep, mask, true
            )
            if (Scheduler.nextTrigger(nr) == null) {
                Toast.makeText(this, "هذا الموعد في الماضي", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (existing != null) {
                val idx = list.indexOfFirst { it.id == nr.id }
                if (idx >= 0) list[idx] = nr else list.add(nr)
            } else {
                list.add(nr)
            }
            Store.save(this, list)
            Scheduler.schedule(this, nr)
            refresh()
            dialog.dismiss()
        }
    }
}
