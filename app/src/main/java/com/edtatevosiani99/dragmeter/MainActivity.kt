package com.edtatevosiani99.dragmeter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity(), LocationListener {
    private lateinit var gauge: SpeedometerView
    private lateinit var speedText: TextView
    private lateinit var runText: TextView
    private lateinit var locationManager: LocationManager
    private var measuring = false
    private var startMs = 0L
    private var bestTime = 0.0
    private var currentSpeed = 0f
    private var dialColor = Color.rgb(42, 177, 255)
    private var needleColor = Color.rgb(255, 63, 68)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 10, 15)
        window.navigationBarColor = Color.rgb(8, 10, 15)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
            setBackgroundColor(Color.rgb(8, 10, 15))
        }
        val header = TextView(this).apply {
            text = "DRAGMETER"; textSize = 24f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; letterSpacing = 0.16f; setPadding(0, dp(6), 0, dp(2))
        }
        val subtitle = TextView(this).apply {
            text = "GPS PERFORMANCE METER"; textSize = 10f
            setTextColor(Color.rgb(125, 145, 164)); gravity = Gravity.CENTER; letterSpacing = 0.18f
        }
        root.addView(header, matchWrap())
        root.addView(subtitle, matchWrap())
        gauge = SpeedometerView(this).apply { dial = dialColor; needle = needleColor }
        root.addView(gauge, LinearLayout.LayoutParams(-1, 0, 1f))
        val infoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        speedText = infoCell("GPS SPEED", "0", "km/h")
        runText = infoCell("0–100 km/h", "--.--", "seconds")
        infoRow.addView(speedText, LinearLayout.LayoutParams(0, dp(76), 1f).apply { marginEnd = dp(5) })
        infoRow.addView(runText, LinearLayout.LayoutParams(0, dp(76), 1f).apply { marginStart = dp(5) })
        root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(8) })
        val startButton = Button(this).apply {
            text = "START 0–100 TEST"; textSize = 14f; setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(15, 119, 202)))
            setOnClickListener { startTest() }
        }
        root.addView(startButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        val settingsButton = Button(this).apply {
            text = "CUSTOMIZE SPEEDOMETER"; textSize = 12f; setTextColor(Color.rgb(195, 210, 225))
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(27, 33, 43)))
            setOnClickListener { showCustomize() }
        }
        root.addView(settingsButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(5) })
        setContentView(root)
        updateCells()
        ensureLocationPermission()
    }

    private fun infoCell(label: String, value: String, unit: String): TextView =
        TextView(this).apply {
            text = "$label\n$value\n$unit"; textSize = 12f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(19, 24, 33)); letterSpacing = 0.02f
        }

    private fun updateCells() {
        speedText.text = "GPS SPEED\n${currentSpeed.toInt()}\nkm/h"
        runText.text = "0–100 km/h\n${if (bestTime > 0) String.format("%.2f", bestTime) else "--.--"}\nseconds"
    }

    private fun startTest() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ensureLocationPermission()
            Toast.makeText(this, "Allow location access to measure GPS speed", Toast.LENGTH_LONG).show()
            return
        }
        measuring = true; startMs = 0L; bestTime = 0.0
        runText.text = "0–100 km/h\nREADY\naccelerate"
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 0f, this)
            Toast.makeText(this, "Test armed — accelerate safely", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "GPS is unavailable. Turn on location.", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 41)
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!location.hasSpeed()) return
        currentSpeed = location.speed * 3.6f
        gauge.speed = currentSpeed
        if (measuring) {
            if (startMs == 0L && currentSpeed >= 1.5f) startMs = SystemClock.elapsedRealtime()
            if (startMs > 0L && currentSpeed >= 100f) {
                bestTime = (SystemClock.elapsedRealtime() - startMs) / 1000.0
                measuring = false
                try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                Toast.makeText(this, "0–100: ${String.format("%.2f", bestTime)} s", Toast.LENGTH_LONG).show()
            }
        }
        updateCells()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun showCustomize() {
        val choices = arrayOf("Dial / scale color", "Needle color", "Reset default colors")
        AlertDialog.Builder(this).setTitle("Customize speedometer").setItems(choices) { _, which ->
            when (which) {
                0 -> chooseColor("Dial / scale color", true)
                1 -> chooseColor("Needle color", false)
                2 -> {
                    dialColor = Color.rgb(42, 177, 255); needleColor = Color.rgb(255, 63, 68)
                    applyColors()
                }
            }
        }.show()
    }

    private fun chooseColor(title: String, isDial: Boolean) {
        val names = arrayOf("Electric blue", "White", "Red", "Green", "Gold", "Purple", "Cyan")
        val colors = intArrayOf(Color.rgb(42,177,255), Color.WHITE, Color.rgb(255,63,68),
            Color.rgb(64,220,130), Color.rgb(255,190,55), Color.rgb(180,100,255), Color.rgb(50,235,230))
        AlertDialog.Builder(this).setTitle(title).setItems(names) { _, index ->
            if (isDial) dialColor = colors[index] else needleColor = colors[index]
            applyColors()
        }.show()
    }

    private fun applyColors() {
        gauge.dial = dialColor; gauge.needle = needleColor; gauge.invalidate()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    override fun onDestroy() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        super.onDestroy()
    }
}

private class SpeedometerView(context: android.content.Context) : View(context) {
    var speed = 0f
        set(value) { field = value.coerceIn(0f, 240f); invalidate() }
    var dial = Color.rgb(42, 177, 255)
    var needle = Color.rgb(255, 63, 68)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()
    private fun color(c: Int, width: Float = 1f, style: Paint.Style = Paint.Style.STROKE) {
        p.color = c; p.strokeWidth = width; p.style = style
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height * 0.49f
        val r = min(width * 0.43f, height * 0.43f)
        if (r <= 0f) return
        color(Color.rgb(17, 21, 29), r, Paint.Style.FILL)
        canvas.drawCircle(cx, cy, r * 1.06f, p)
        color(Color.rgb(36, 43, 54), 1f)
        var yy = cy - r
        while (yy < cy + r) {
            canvas.drawLine(cx - r, yy, cx + r, yy + r * 0.10f, p)
            yy += 7f * resources.displayMetrics.density
        }
        color(Color.rgb(4, 6, 10), r * 0.07f)
        canvas.drawCircle(cx, cy, r * 0.97f, p)
        val startAngle = 135f; val sweep = 270f
        arcRect.set(cx-r*0.91f, cy-r*0.91f, cx+r*0.91f, cy+r*0.91f)
        color(Color.rgb(48,57,69), r*0.055f); canvas.drawArc(arcRect, startAngle, sweep, false, p)
        color(dial, r*0.022f); canvas.drawArc(arcRect, startAngle, sweep*(speed/240f), false, p)
        for (i in 0..48) {
            val angle = Math.toRadians((startAngle + sweep*i/48f).toDouble())
            val major = i % 4 == 0
            val outer = r*0.84f; val inner = if (major) r*0.69f else r*0.76f
            val x1 = cx+cos(angle).toFloat()*inner; val y1 = cy+sin(angle).toFloat()*inner
            val x2 = cx+cos(angle).toFloat()*outer; val y2 = cy+sin(angle).toFloat()*outer
            color(if (major) dial else Color.rgb(106,120,137), if (major) r*0.014f else r*0.007f)
            canvas.drawLine(x1,y1,x2,y2,p)
            if (major) {
                p.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                p.textSize = r*0.075f; p.textAlign = Paint.Align.CENTER; p.color = Color.rgb(220,230,240)
                canvas.drawText((i*5).toString(), cx+cos(angle).toFloat()*r*0.61f,
                    cy+sin(angle).toFloat()*r*0.61f+p.textSize*0.34f,p)
            }
        }
        color(Color.rgb(26,34,45), r*0.012f, Paint.Style.FILL)
        val box = RectF(cx-r*0.43f, cy-r*0.20f, cx+r*0.43f, cy+r*0.25f)
        canvas.drawRoundRect(box,r*0.08f,r*0.08f,p)
        color(Color.rgb(60,75,91),r*0.008f); canvas.drawRoundRect(box,r*0.08f,r*0.08f,p)
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        p.textSize = r*0.29f; canvas.drawText(speed.toInt().toString(),cx,cy+r*0.055f,p)
        p.textSize = r*0.065f; p.color = dial; canvas.drawText("km/h  •  GPS",cx,cy+r*0.17f,p)

        // Pointer remains on the outer arc, so the center readout stays unobstructed.
        val a = Math.toRadians((startAngle+sweep*(speed/240f)).toDouble())
        val tipR = r*0.85f; val tailR = r*0.64f; val halfWidth = r*0.022f
        val tipX = cx+cos(a).toFloat()*tipR; val tipY = cy+sin(a).toFloat()*tipR
        val tailX = cx+cos(a).toFloat()*tailR; val tailY = cy+sin(a).toFloat()*tailR
        val px = (-sin(a)).toFloat()*halfWidth; val py = cos(a).toFloat()*halfWidth
        val path = Path().apply {
            moveTo(tipX,tipY); lineTo(tailX+px,tailY+py); lineTo(tailX-px,tailY-py); close()
        }
        color(needle,1f,Paint.Style.FILL); canvas.drawPath(path,p)
        color(Color.rgb(126,143,160),r*0.07f,Paint.Style.FILL)
        canvas.drawText("DRAGMETER",cx,cy+r*0.49f,p)
        p.textSize = r*0.055f; p.color = dial
        canvas.drawText("0     100     200     240",cx,cy+r*0.60f,p)
    }
}
