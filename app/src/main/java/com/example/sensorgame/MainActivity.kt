package com.example.sensorgame

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")

class MainActivity : AppCompatActivity(), SensorEventListener {   // Step 1

    // ── From skeleton ──────────────────────────────────────────────────────

    private lateinit var gameArea: FrameLayout

    private lateinit var ball: View

    private var ballX = 0f

    private var ballY = 0f

    private var ballPx = 0

    private lateinit var tvGps: TextView

    private lateinit var tvScore: TextView

    // ── Added in Step 2 ────────────────────────────────────────────────────

    private lateinit var sensorManager: SensorManager

    private var gyroscope: Sensor? = null

    private var hasGyroscope = false

    private var gyroX = 0f   // pitch: forward/back tilt → up/down ball movement

    private var gyroY = 0f   // roll:  left/right tilt  → left/right ball movement

    private val handler = Handler(Looper.getMainLooper())

    private var loopRunnable: Runnable? = null
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L

    // ball color change
    private var colorIndex = 0
    private val ballColors = intArrayOf(
        0xFF33B5E5.toInt(),
        0xFFE533B5.toInt(),
        0xFFFF8800.toInt(),
        0xFF009900.toInt()
    )
    private val stars = mutableListOf<View>()
    private var starPx = 0
    companion object {   // Step 2
        private const val BASE_SPEED = 5f
        private const val SHAKE_THRESHOLD = 12f
        private const val SHAKE_COOLDOWN_MS = 1500L
        private const val STAR_COUNT = 5

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gameArea = findViewById(R.id.gameArea)
        ball     = findViewById(R.id.ball)
        tvGps    = findViewById(R.id.tvGps)
        tvScore  = findViewById(R.id.tvScore)
        initSensors()          // Step 3: new line
        setupTestControls()
        gameArea.post { startGame() }   // Step 3: was initBall()
    }

    override fun onResume() {   // Step 4
        super.onResume()
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }



    override fun onPause() {   // Step 4
        super.onPause()
        sensorManager.unregisterListener(this)
        loopRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {   // Step 4
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun initSensors() {   // Step 5
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        hasGyroscope  = gyroscope != null

        if (!hasGyroscope) Toast.makeText(this, "No gyroscope — use touch controls", Toast.LENGTH_LONG).show()
    }

    private fun startGame() {   // Step 6 — replaces initBall()
        val w = gameArea.width; val h = gameArea.height

        if (w == 0 || h == 0) { gameArea.post { startGame() }; return }
        val density = resources.displayMetrics.density
        starPx = (48 * density).toInt()
        ballPx = ball.width
        ballX  = (w - ballPx) / 2f
        ballY  = (h - ballPx) / 2f
        ball.translationX = ballX
        ball.translationY = ballY
        applyBallColor()
        tvScore.text = "Score: 0"
        spawnStars(w,h)
        loopRunnable?.let { handler.removeCallbacks(it) }
        loopRunnable = object : Runnable {
            override fun run() {
                moveBallWithGyro()
                handler.postDelayed(this, 33L)
            }
        }

        handler.post(loopRunnable!!)
    }

    private fun moveBallWithGyro() {   // Step 7 stub
        if (!hasGyroscope) return

        ballX = (ballX + gyroY * BASE_SPEED).coerceIn(0f, (gameArea.width - ballPx).toFloat())
        ballY = (ballY + gyroX * BASE_SPEED).coerceIn(0f, (gameArea.height - ballPx).toFloat())

        // Update positions
        ball.translationX = ballX
        ball.translationY = ballY
    }

    override fun onSensorChanged(event: SensorEvent) {   // Step 7 stub
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                checkStarCollisions()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val currentTime = System.currentTimeMillis()
                val acceleration = sqrt((ax * ax) + (ay * ay) + (az * az))
                if (((acceleration - SensorManager.GRAVITY_EARTH) > SHAKE_THRESHOLD) && ((currentTime - lastShakeTime) > SHAKE_COOLDOWN_MS)) {
                    lastShakeTime = currentTime
                    onShakeDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}   // Step 7

    private fun onShakeDetected(){
        colorIndex = if (colorIndex < 3) colorIndex+1 else 0
        applyBallColor()
        Toast.makeText(this,"Shake detected!",Toast.LENGTH_SHORT).show()
    }

    private fun applyBallColor() {
        ball.background = GradientDrawable().apply{
            shape = GradientDrawable.OVAL
            setColor(ballColors[colorIndex])
        }
    }

    private fun spawnStars(areaW:Int, areaH:Int) {
        stars.forEach{ gameArea.removeView(it) }
        stars.clear()

        val bottomMarginPx = (120*resources.displayMetrics.density).toInt()
        val safeH = (areaH - bottomMarginPx).coerceAtLeast(starPx * 2)

        repeat(STAR_COUNT){
            val star = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(starPx, starPx)
                setBackgroundResource(R.drawable.star_shape)
                translationX = (Math.random() * (areaW - starPx)).toFloat()
                translationY = (Math.random() * (safeH - starPx)).toFloat()
            }
            stars.add(star)
            gameArea.addView(star)
        }
    }

    private fun checkStarCollisions(){
        val ballRight = ballX + ballPx
        val ballBottom = ballY + ballPx
        val collected = mutableListOf<View>()

        for (star in stars){
            val sx = star.translationX
            val sy = star.translationY
            if (ballX < sx + starPx && ballRight > sx && ballY < sy + starPx && ballBottom > sy){
                collected.add(star)
            }
        }

        for (star in collected){
            gameArea.removeView(star)
            stars.remove(star)

            Toast.makeText(this, "Star collected!", Toast.LENGTH_LONG).show()
        }

        if (stars.isEmpty()) spawnStars(gameArea.width, gameArea.height)
    }

    private fun setupTestControls() {   // UNCHANGED from skeleton
        gameArea.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                if (ballPx > 0) {
                    ballX = (event.x - ballPx / 2f).coerceIn(0f, (gameArea.width  - ballPx).toFloat())
                    ballY = (event.y - ballPx / 2f).coerceIn(0f, (gameArea.height - ballPx).toFloat())
                    ball.translationX = ballX
                    ball.translationY = ballY
                }
            }
            true
        }
        findViewById<Button>(R.id.btnShake).isEnabled = false
        findViewById<Button>(R.id.btnGps).isEnabled = false
    }
}