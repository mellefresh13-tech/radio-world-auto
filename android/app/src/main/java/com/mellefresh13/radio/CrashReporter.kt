package com.mellefresh13.radio

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves the last uncaught exception to a file and shows it on the next launch (copyable). */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE_NAME).writeText(
                    "Thread: ${thread.name}\n${sw.toString().take(8000)}"
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun showPending(activity: Activity) {
        val file = File(activity.filesDir, FILE_NAME)
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        file.delete()
        if (text.isBlank()) return
        val view = TextView(activity).apply {
            this.text = text
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(activity)
            .setTitle("App crashed last time")
            .setView(ScrollView(activity).apply { addView(view) })
            .setPositiveButton("Copy") { _, _ ->
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
