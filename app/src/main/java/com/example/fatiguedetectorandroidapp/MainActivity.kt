package com.example.fatiguedetectorandroidapp

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.media.MediaPlayer
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import java.io.InputStream
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    lateinit var faceLandmarker: FaceLandmarker

    // 🔴 Camera fatigue
    var closedEyeFrames = 0
    val EAR_THRESHOLD = 0.18
    val FATIGUE_FRAME_LIMIT = 70
    var fatigueDetected by mutableStateOf(false)

    // 🔵 Bluetooth
    lateinit var bluetoothAdapter: BluetoothAdapter
    var bluetoothSocket: BluetoothSocket? = null
    var inputStream: InputStream? = null
    var discoveredDevices = mutableStateListOf<BluetoothDevice>()
    var espFatigueDetected by mutableStateOf(false)

    // 🔔 Alert
    var alertTriggered = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->

                val faces = result.faceLandmarks()

                if (faces.isNotEmpty()) {
                    val face = faces[0]

                    val leftEAR = calculateEAR(
                        face[33].x(), face[33].y(),
                        face[160].x(), face[160].y(),
                        face[158].x(), face[158].y(),
                        face[133].x(), face[133].y(),
                        face[153].x(), face[153].y(),
                        face[144].x(), face[144].y()
                    )

                    if (leftEAR < EAR_THRESHOLD) {
                        closedEyeFrames++

                        if (closedEyeFrames > FATIGUE_FRAME_LIMIT) {
                            fatigueDetected = true
                            triggerAlertIfNeeded()
                        }
                    } else {
                        closedEyeFrames = 0
                        fatigueDetected = false
                        alertTriggered = false
                        stopAlert()
                    }
                }
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(this, options)

        setContent {
            PermissionScreen()
        }
    }

    // 🔍 SCAN BLUETOOTH DEVICES
    fun startBluetoothScan(context: Context) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        discoveredDevices.clear()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    device?.let {
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)

        bluetoothAdapter.startDiscovery()
    }

    // 🔗 CONNECT SELECTED DEVICE
    fun connectToSelectedDevice(device: BluetoothDevice) {
        try {
            val uuid = java.util.UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )

            bluetoothSocket =
                device.createRfcommSocketToServiceRecord(uuid)

            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream

            readBluetoothData()

        } catch (e: Exception) {
            Log.e("BT_CONNECT", e.toString())
        }
    }

    // 📡 READ DATA
    fun readBluetoothData() {
        Thread {
            val buffer = ByteArray(1024)

            while (true) {
                val bytes = inputStream?.read(buffer) ?: 0
                val message = String(buffer, 0, bytes)

                Log.d("BT_DATA", message)

                runOnUiThread {
                    processESPData(message)
                }
            }
        }.start()
    }

    fun processESPData(data: String) {
        if (data.contains("ALERT:FATIGUE")) {
            espFatigueDetected = true
            triggerAlertIfNeeded()
        }

        if (data.contains("ALERT:NORMAL")) {
            espFatigueDetected = false
            stopAlert()
        }
    }

    // 🔥 FINAL ALERT
    fun triggerAlertIfNeeded() {
        val finalFatigue = fatigueDetected || espFatigueDetected

        if (finalFatigue && !alertTriggered) {
            triggerAlert()
            alertTriggered = true
        }
    }

    fun triggerAlert() {
        if (mediaPlayer?.isPlaying == true) return

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(
                this,
                Settings.System.DEFAULT_ALARM_ALERT_URI
            )
            mediaPlayer?.isLooping = true
        }

        mediaPlayer?.start()

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                1500,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    fun stopAlert() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                    it.prepare()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun PermissionScreen() {

    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission =
            permissions[Manifest.permission.CAMERA] == true &&
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.VIBRATE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }) {
                Text("Allow Required Permissions")
            }
        }
    } else {
        FatigueScreen()
    }
}

@Composable
fun FatigueScreen() {

    val context = LocalContext.current as MainActivity
    var startMonitoring by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text("Fatigue Detector", fontSize = 30.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            context.startBluetoothScan(context)
        }) {
            Text("Scan Devices")
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 🔥 Device List
        context.discoveredDevices.forEach { device ->
            Button(onClick = {
                context.connectToSelectedDevice(device)
            }) {
                Text(device.name ?: "Unknown Device")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(onClick = { startMonitoring = true }) {
            Text("Start Monitoring")
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (startMonitoring) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {

                CameraPreview(context)

                val finalFatigue =
                    context.fatigueDetected || context.espFatigueDetected

                if (finalFatigue) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚠ DRIVER DROWSY",
                            fontSize = 28.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
fun calculateEAR(
    p1x: Float, p1y: Float,
    p2x: Float, p2y: Float,
    p3x: Float, p3y: Float,
    p4x: Float, p4y: Float,
    p5x: Float, p5y: Float,
    p6x: Float, p6y: Float
): Double {

    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        return kotlin.math.sqrt(
            ((x2 - x1)*(x2 - x1) + (y2 - y1)*(y2 - y1)).toDouble()
        )
    }

    val vertical1 = dist(p2x,p2y,p6x,p6y)
    val vertical2 = dist(p3x,p3y,p5x,p5y)
    val horizontal = dist(p1x,p1y,p4x,p4y)

    return (vertical1 + vertical2) / (2.0 * horizontal)
}
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(activity: MainActivity) {

    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->

            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({

                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .setOutputImageFormat(
                        ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                    )
                    .build()

                imageAnalyzer.setAnalyzer(
                    ContextCompat.getMainExecutor(ctx)
                ) { imageProxy ->

                    val mediaImage = imageProxy.image

                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        val mpImage = MediaImageBuilder(mediaImage).build()

                        activity.faceLandmarker.detectAsync(
                            mpImage,
                            System.currentTimeMillis()
                        )

                    } catch (e: Exception) {
                        Log.e("CAMERA_ERROR", e.toString())
                    }

                    imageProxy.close()
                }

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalyzer
                )

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}