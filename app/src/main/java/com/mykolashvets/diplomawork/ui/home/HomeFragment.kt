package com.mykolashvets.diplomawork.ui.home


import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.mykolashvets.diplomawork.databinding.FragmentHomeBinding
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var previewView: PreviewView
    private lateinit var interpreter: Interpreter
    private lateinit var labels: List<String>

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MODEL_INPUT_SIZE = 224
        private const val MODEL_INPUT_CHANNELS = 3
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(requireContext().contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(requireContext().contentResolver, it)
            }
            processBitmap(bitmap)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingInflatedId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = binding.previewView
        labels = loadLabels()

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        interpreter = Interpreter(loadModelFile("model.tflite"))

        binding.btnLiveScan.setOnClickListener {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
        }

        binding.btnGalleryPick.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun loadLabels(): List<String> {
        return requireContext().assets.open("labels.txt").bufferedReader().useLines { it.toList() }
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fileDescriptor = requireContext().assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap() ?: run {
            imageProxy.close()
            return
        }

        processBitmap(bitmap)
        imageProxy.close()
    }

    private fun processBitmap(bitmap: Bitmap) {
        val resized = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val byteBuffer = convertBitmapToByteBuffer(resized)

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(byteBuffer, output)

        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        val confidence = if (maxIndex >= 0) output[0][maxIndex] else 0f
        val label = if (maxIndex in labels.indices) labels[maxIndex] else "Невідомо"

        requireActivity().runOnUiThread {
            binding.resultText.apply {
                text = "$label: ${(confidence * 100).toInt()}%"
                visibility = View.VISIBLE
            }
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * MODEL_INPUT_CHANNELS)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF) / 255f
            val g = (pixelValue shr 8 and 0xFF) / 255f
            val b = (pixelValue and 0xFF) / 255f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        return byteBuffer
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
