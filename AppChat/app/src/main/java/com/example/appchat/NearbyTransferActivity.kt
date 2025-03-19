package com.example.appchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appchat.databinding.ActivityNearbyTransferBinding
import com.example.appchat.util.UserPreferences
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.File
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentResolver
import android.webkit.MimeTypeMap
import java.io.FileOutputStream
import java.io.InputStream
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Build
import android.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import androidx.annotation.OptIn
import org.json.JSONObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.integration.android.IntentIntegrator
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class NearbyTransferActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNearbyTransferBinding
    private lateinit var nearbyConnectionsClient: ConnectionsClient
    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "com.example.appchat.nearby"
    private var currentEndpointId: String? = null
    private var isSendMode = false
    private var isAdvertising = false
    private var isDiscovering = false
    private var selectedFileUri: Uri? = null
    private lateinit var qrCodeImageView: ImageView
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            selectedFileUri = it
            // 选择文件后开始广播
            if (checkAndRequestPermissions()) {
                isSendMode = true
                startAdvertising()
            }
        }
    }

    // 添加一个Map来存储发现的设备
    private val discoveredEndpoints = mutableMapOf<String, DiscoveredEndpointInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化 Nearby Connections
        initNearbyConnections()
        
        binding.btnSend.setOnClickListener { 
            if (isAdvertising) {
                stopAdvertising()
            } else {
                showFileChooser()
            }
        }
        
        binding.btnReceive.setOnClickListener { 
            if (isDiscovering) {
                stopDiscovery()
            } else {
                startQrCodeScanner()
            }
        }
    }
    
    private fun initNearbyConnections() {
        nearbyConnectionsClient = Nearby.getConnectionsClient(this)
        // 确保初始状态清理
        try {
            nearbyConnectionsClient.stopAllEndpoints()
            nearbyConnectionsClient.stopAdvertising()
            nearbyConnectionsClient.stopDiscovery()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()

        // 基本权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Android 12 及以上需要的蓝牙和 Wi-Fi 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        // 存储权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
                return false
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            return false
        }

        // 检查蓝牙是否开启
        return checkBluetoothEnabled()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 所有权限都已授予
                binding.statusText.text = "权限已授予，请重试"
            } else {
                // 有权限被拒绝
                binding.statusText.text = "需要必要权限才能使用面对面快传"
                Toast.makeText(this, "需要必要权限才能使用面对面快传", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startAdvertising() {
        if (isAdvertising) {
            binding.statusText.text = "已在等待连接中..."
            return
        }

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()
        
        nearbyConnectionsClient.startAdvertising(
            UserPreferences.getUsername(this),
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            isAdvertising = true
            binding.btnSend.text = "停止发送"
            binding.btnReceive.isEnabled = false
            binding.statusText.text = "等待接收方扫描二维码..."
            
            // 生成包含连接信息的二维码
            generateQrCode()
        }.addOnFailureListener { e ->
            isAdvertising = false
            binding.statusText.text = "广播失败: ${e.message}"
            e.printStackTrace()
        }
    }
    
    private fun generateQrCode() {
        try {
            // 创建连接信息
            val connectionInfo = JSONObject().apply {
                put("username", UserPreferences.getUsername(this@NearbyTransferActivity))
                put("serviceId", SERVICE_ID)
                // 可以添加其他需要的信息
            }

            // 使用 ZXing 生成二维码
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(
                connectionInfo.toString(),
                BarcodeFormat.QR_CODE,
                300,
                300
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            // 显示二维码
            binding.qrCodeImageView.setImageBitmap(bitmap)
            binding.qrCodeImageView.visibility = View.VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "生成二维码失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startQrCodeScanner() {
        if (!checkCameraPermission()) {
            requestCameraPermission()
            return
        }

        // 先显示预览视图
        binding.previewView.visibility = View.VISIBLE
        binding.qrCodeImageView.visibility = View.GONE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "相机初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.previewView.visibility = View.GONE
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        try {
            // 先解绑所有用例
            cameraProvider.unbindAll()

            // 设置预览
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()

            // 设置图像分析
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // 设置相机选择器
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // 绑定预览用例
            preview?.setSurfaceProvider(binding.previewView.surfaceProvider)

            // 绑定图像分析用例
            imageAnalysis?.setAnalyzer(
                ContextCompat.getMainExecutor(this)
            ) { imageProxy ->
                processQrCode(imageProxy)
            }

            // 绑定到生命周期
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "相机绑定失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    @OptIn(ExperimentalGetImage::class) private fun processQrCode(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { rawValue ->
                            try {
                                val connectionInfo = JSONObject(rawValue)
                                val username = connectionInfo.getString("username")
                                val serviceId = connectionInfo.getString("serviceId")

                                // 停止扫描
                                stopQrCodeScanner()

                                // 开始搜索并连接
                                if (checkAndRequestPermissions()) {
                                    isSendMode = false
                                    // 延迟一下再开始搜索
                                    binding.root.postDelayed({
                                        startDiscovery()
                                    }, 500)
                                }
                                
                                // 不要继续处理其他二维码
                                return@addOnSuccessListener
                            } catch (e: Exception) {
                                e.printStackTrace()
                                runOnUiThread {
                                    Toast.makeText(this, "无效的二维码", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    private fun stopQrCodeScanner() {
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            preview = null
            imageAnalysis = null
            camera = null
            binding.previewView.visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            try {
                // 解析二维码中的连接信息
                val connectionInfo = JSONObject(result.contents)
                val username = connectionInfo.getString("username")
                val serviceId = connectionInfo.getString("serviceId")

                // 开始搜索并连接
                if (checkAndRequestPermissions()) {
                    isSendMode = false
                    startDiscovery()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无效的二维码", Toast.LENGTH_SHORT).show()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    
    private fun startDiscovery() {
        if (!checkAndRequestPermissions()) {
            return
        }

        if (isDiscovering) {
            binding.statusText.text = "已在搜索中..."
            return
        }

        // 先确保清理所有连接状态
        try {
            binding.statusText.text = "正在初始化..."
            
            // 先停止所有连接
            stopAllConnections()
            
            // 等待一段时间后再开始搜索
            binding.root.postDelayed({
                try {
                    val discoveryOptions = DiscoveryOptions.Builder()
                        .setStrategy(STRATEGY)
                        .build()

                    nearbyConnectionsClient.startDiscovery(
                        SERVICE_ID,
                        endpointDiscoveryCallback,
                        discoveryOptions
                    ).addOnSuccessListener {
                        isDiscovering = true
                        binding.btnReceive.text = "停止接收"
                        binding.btnSend.isEnabled = false
                        binding.statusText.text = "正在搜索附近设备..."
                    }.addOnFailureListener { e ->
                        isDiscovering = false
                        binding.btnReceive.text = "接收文件"
                        binding.btnSend.isEnabled = true
                        binding.statusText.text = "搜索失败: ${e.message}"
                        e.printStackTrace()
                        
                        when {
                            e.message?.contains("8029") == true -> {
                                // 重新初始化并延迟重试
                                initNearbyConnections()
                                binding.root.postDelayed({
                                    if (checkAndRequestPermissions()) {
                                        startDiscovery()
                                    }
                                }, 3000)
                            }
                            e.message?.contains("BLUETOOTH") == true -> {
                                checkBluetoothEnabled()
                            }
                            else -> {
                                Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    binding.statusText.text = "搜索初始化失败: ${e.message}"
                }
            }, 2000)
        } catch (e: Exception) {
            e.printStackTrace()
            binding.statusText.text = "初始化失败: ${e.message}"
        }
    }
    
    private fun stopAllConnections() {
        try {
            // 先停止所有端点连接
            nearbyConnectionsClient.stopAllEndpoints()
            
            // 延迟一下再停止广播和发现
            binding.root.postDelayed({
                try {
                    nearbyConnectionsClient.stopAdvertising()
                    nearbyConnectionsClient.stopDiscovery()
                    
                    // 重置所有状态
                    isAdvertising = false
                    isDiscovering = false
                    currentEndpointId = null
                    selectedFileUri = null
                    discoveredEndpoints.clear()
                    
                    // 重置UI状态
                    binding.btnSend.text = "发送文件"
                    binding.btnReceive.text = "接收文件"
                    binding.btnSend.isEnabled = true
                    binding.btnReceive.isEnabled = true
                    binding.progressBar.progress = 0
                    binding.qrCodeImageView.visibility = View.GONE
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun checkBluetoothEnabled(): Boolean {
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (!bluetoothAdapter.isEnabled) {
            try {
                val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBtIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法开启蓝牙", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return true
    }
    
    override fun onResume() {
        super.onResume()
        // 在恢复时检查蓝牙状态
        if (isDiscovering || isAdvertising) {
            if (!checkBluetoothEnabled()) {
                stopAllConnections()
            }
        }
    }
    
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            discoveredEndpoints[endpointId] = info
            if (isSendMode) {
                // 发送方发现接收方时，显示选择对话框
                showEndpointChoiceDialog(endpointId, info)
            } else {
                // 接收方发现发送方时，显示连接确认对话框
                runOnUiThread {
                    AlertDialog.Builder(this@NearbyTransferActivity)
                        .setTitle("发现设备")
                        .setMessage("是否连接到设备：${info.endpointName}？")
                        .setPositiveButton("连接") { _, _ ->
                            // 停止搜索，开始连接
                            stopDiscovery()
                            nearbyConnectionsClient.requestConnection(
                                UserPreferences.getUsername(this@NearbyTransferActivity),
                                endpointId,
                                connectionLifecycleCallback
                            ).addOnSuccessListener {
                                binding.statusText.text = "正在连接到 ${info.endpointName}..."
                            }.addOnFailureListener { e ->
                                binding.statusText.text = "连接请求失败: ${e.message}"
                                // 连接失败时重新开始搜索
                                startDiscovery()
                            }
                        }
                        .setNegativeButton("取消") { _, _ ->
                            // 继续搜索其他设备
                        }
                        .show()
                }
            }
        }
        
        override fun onEndpointLost(endpointId: String) {
            discoveredEndpoints.remove(endpointId)
            runOnUiThread {
                if (isDiscovering) {
                    binding.statusText.text = "设备已离开，继续搜索..."
                }
            }
        }
    }
    
    private fun showEndpointChoiceDialog(endpointId: String, info: DiscoveredEndpointInfo) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("选择接收方")
                .setMessage("是否连接到设备：${info.endpointName}？")
                .setPositiveButton("连接") { _, _ ->
                    nearbyConnectionsClient.requestConnection(
                        UserPreferences.getUsername(this),
                        endpointId,
                        connectionLifecycleCallback
                    ).addOnSuccessListener {
                        binding.statusText.text = "正在连接到 ${info.endpointName}..."
                    }.addOnFailureListener { e ->
                        binding.statusText.text = "连接请求失败: ${e.message}"
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            currentEndpointId = endpointId
            runOnUiThread {
                AlertDialog.Builder(this@NearbyTransferActivity)
                    .setTitle("连接请求")
                    .setMessage(if (isSendMode) 
                        "确认连接到 ${info.endpointName}？" 
                    else 
                        "是否接受来自 ${info.endpointName} 的连接请求？")
                    .setPositiveButton("接受") { _, _ ->
                        nearbyConnectionsClient.acceptConnection(endpointId, payloadCallback)
                        binding.statusText.text = "正在建立连接..."
                    }
                    .setNegativeButton("拒绝") { _, _ ->
                        nearbyConnectionsClient.rejectConnection(endpointId)
                        // 如果是接收方，拒绝后重新开始搜索
                        if (!isSendMode) {
                            startDiscovery()
                        }
                    }
                    .setCancelable(false)
                    .show()
            }
        }
        
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                binding.statusText.text = "连接成功"
                if (isSendMode && selectedFileUri != null) {
                    // 连接成功后直接发送已选择的文件
                    handleSelectedFile(selectedFileUri!!)
                } else {
                    binding.statusText.text = "等待对方发送文件..."
                }
                // 连接成功后停止广播和发现
                stopAdvertising()
                stopDiscovery()
            } else {
                binding.statusText.text = "连接失败: ${result.status}"
            }
        }
        
        override fun onDisconnected(endpointId: String) {
            binding.statusText.text = "连接已断开"
            currentEndpointId = null
            // 重置按钮状态
            binding.btnSend.text = "发送文件"
            binding.btnReceive.text = "接收文件"
            binding.btnSend.isEnabled = true
            binding.btnReceive.isEnabled = true
            isAdvertising = false
            isDiscovering = false
        }
    }
    
    private fun showFileChooser() {
        filePickerLauncher.launch("*/*")
    }
    
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = getFileName(uri)
            val file = File.createTempFile("nearby_", "_$fileName", cacheDir)
            
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "准备文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result ?: "unknown_file"
    }
    
    private fun handleSelectedFile(uri: Uri) {
        try {
            currentEndpointId?.let { endpointId ->
                val inputStream = contentResolver.openInputStream(uri)
                val fileName = getFileName(uri)
                
                inputStream?.use { input ->
                    val file = File(cacheDir, fileName).apply {
                        outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val filePayload = Payload.fromFile(file)
                    nearbyConnectionsClient.sendPayload(endpointId, filePayload)
                        .addOnSuccessListener {
                            binding.statusText.text = "开始发送文件: $fileName"
                        }
                        .addOnFailureListener { e ->
                            binding.statusText.text = "发送失败: ${e.message}"
                            e.printStackTrace()
                        }
                }
            } ?: run {
                binding.statusText.text = "未连接到接收方"
                Toast.makeText(this, "请等待接收方连接", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.statusText.text = "文件处理失败: ${e.message}"
            Toast.makeText(this, "文件处理失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                val payloadFile = payload.asFile()
                payloadFile?.asJavaFile()?.let { file ->
                    runOnUiThread {
                        binding.statusText.text = "正在接收文件..."
                    }
                    try {
                        // 确保下载目录存在
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadDir.exists()) {
                            downloadDir.mkdirs()
                        }

                        // 生成唯一文件名
                        val fileName = generateUniqueFileName(file.name, downloadDir)
                        val destinationFile = File(downloadDir, fileName)

                        // 使用输入输出流复制文件
                        file.inputStream().use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // 通知媒体库更新
                        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        intent.data = Uri.fromFile(destinationFile)
                        sendBroadcast(intent)

                        runOnUiThread {
                            binding.statusText.text = "文件已保存: $fileName"
                            Toast.makeText(this@NearbyTransferActivity,
                                "文件已保存至下载目录", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            binding.statusText.text = "保存文件失败: ${e.message}"
                            Toast.makeText(this@NearbyTransferActivity,
                                "保存文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val progress = (update.bytesTransferred * 100 / update.totalBytes).toInt()
            runOnUiThread {
                binding.progressBar.progress = progress
                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS ->
                        binding.statusText.text = "传输进度: $progress%"
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        binding.statusText.text = if (isSendMode) "发送完成" else "接收完成"
                        binding.progressBar.progress = 100
                    }
                    PayloadTransferUpdate.Status.FAILURE -> {
                        binding.statusText.text = "传输失败"
                        Toast.makeText(this@NearbyTransferActivity, 
                            "传输失败", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun generateUniqueFileName(originalName: String, directory: File): String {
        var fileName = originalName
        var file = File(directory, fileName)
        var counter = 1
        
        while (file.exists()) {
            val dotIndex = originalName.lastIndexOf('.')
            fileName = if (dotIndex != -1) {
                val name = originalName.substring(0, dotIndex)
                val extension = originalName.substring(dotIndex)
                "${name}_${counter}${extension}"
            } else {
                "${originalName}_${counter}"
            }
            file = File(directory, fileName)
            counter++
        }
        
        return fileName
    }
    
    private fun stopAdvertising() {
        try {
            nearbyConnectionsClient.stopAdvertising()
            isAdvertising = false
            binding.btnSend.text = "发送文件"
            binding.btnReceive.isEnabled = true
            binding.statusText.text = "已停止发送"
            selectedFileUri = null  // 清除已选择的文件
            binding.qrCodeImageView.visibility = View.GONE  // 隐藏二维码
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun stopDiscovery() {
        try {
            nearbyConnectionsClient.stopDiscovery()
            isDiscovering = false
            binding.btnReceive.text = "接收文件"
            binding.btnSend.isEnabled = true
            binding.statusText.text = "已停止搜索"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopQrCodeScanner()
        stopAdvertising()
        stopDiscovery()
        nearbyConnectionsClient.stopAllEndpoints()
        discoveredEndpoints.clear()
        selectedFileUri = null
    }
    
    companion object {
        private const val REQUEST_FILE_CODE = 123
        private const val PERMISSION_REQUEST_CODE = 456
        private const val CAMERA_PERMISSION_REQUEST_CODE = 789
    }
} 