package com.mykolashvets.diplomawork.ui.home


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Build
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
        private const val REQUEST_CODE_CAMERA_PERMISSIONS = 10
        private const val REQUEST_CODE_STORAGE_PERMISSIONS = 11
        private val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        private const val MODEL_INPUT_SIZE = 224
        private const val MODEL_INPUT_CHANNELS = 3
        private const val PREFS = "mushroom_prefs"
        private const val KEY_HISTORY = "history_set"
    }

    // ===== SharedPreferences helpers =====
    private fun saveMushroom(name: String) {
        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_HISTORY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(name)
        prefs.edit().putStringSet(KEY_HISTORY, set).apply()
    }

    // Лончер для вибору зображення з галереї
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(requireContext(), "Не вибрано фото", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
        try {
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val src = ImageDecoder.createSource(requireContext().contentResolver, uri)
                ImageDecoder.decodeBitmap(src)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            }
            processBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Gallery decode error", e)
            Toast.makeText(requireContext(), "Не вдалося обробити фото", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewView = binding.previewView
        labels = loadLabels()
        interpreter = Interpreter(loadModelFile("model.tflite"))

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets }

        binding.btnLiveScan.setOnClickListener {
            if (allGranted(CAMERA_PERMISSIONS)) startCamera()
            else requestPermissions(CAMERA_PERMISSIONS, REQUEST_CODE_CAMERA_PERMISSIONS)
        }
        binding.btnGalleryPick.setOnClickListener {
            if (allGranted(STORAGE_PERMISSIONS)) pickImageLauncher.launch("image/*")
            else requestPermissions(STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE_PERMISSIONS)
        }
    }

    private fun loadLabels(): List<String> = requireContext().assets.open("labels.txt").bufferedReader().useLines { it.toList() }

    private fun loadModelFile(name: String): MappedByteBuffer {
        val fd = requireContext().assets.openFd(name); val inStream = FileInputStream(fd.fileDescriptor)
        return inStream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun allGranted(perms: Array<String>) = perms.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(req: Int, perms: Array<String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        when (req) {
            REQUEST_CODE_CAMERA_PERMISSIONS -> if (allGranted(CAMERA_PERMISSIONS)) startCamera()
            REQUEST_CODE_STORAGE_PERMISSIONS -> if (allGranted(STORAGE_PERMISSIONS)) pickImageLauncher.launch("image/*")
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analyzer.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { proxy -> handleImageProxy(proxy) }
            try { provider.unbindAll(); provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer) }
            catch (e: Exception) { Log.e("HomeFragment", "Cam bind fail", e) }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleImageProxy(proxy: ImageProxy) { proxy.toBitmap()?.let { processBitmap(it) }; proxy.close() }

    private fun processBitmap(bmp: Bitmap) {
        val input = Bitmap.createScaledBitmap(bmp, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val buf = convertBitmapToByteBuffer(input)
        val out = Array(1) { FloatArray(labels.size) }; interpreter.run(buf, out)
        val idx = out[0].indices.maxByOrNull { out[0][it] } ?: -1
        val conf = if (idx >= 0) out[0][idx] else 0f
        val label = if (idx in labels.indices) labels[idx] else "Невідомо"
        // ===== save to SharedPreferences =====
        saveMushroom(label)
        // ===== show result =====
        requireActivity().runOnUiThread {
            binding.resultText.text = "$label: ${(conf * 100).toInt()}%"; binding.resultText.visibility = View.VISIBLE }
    }

    private fun ImageProxy.toBitmap(): Bitmap? = try {
        val y = planes[0].buffer; val u = planes[1].buffer; val v = planes[2].buffer
        val ySize = y.remaining(); val uSize = u.remaining(); val vSize = v.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        y.get(nv21, 0, ySize); v.get(nv21, ySize, vSize); u.get(nv21, ySize + vSize, uSize)
        val yuv = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream(); yuv.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        android.graphics.BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) }
    catch (e: Exception) { Log.e("HomeFragment", "toBitmap err", e); null }

    private fun convertBitmapToByteBuffer(bmp: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * MODEL_INPUT_CHANNELS).apply { order(ByteOrder.nativeOrder()) }
        val intValues = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE); bmp.getPixels(intValues, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (pix in intValues) {
            buf.putFloat(((pix shr 16) and 0xFF) / 255f); buf.putFloat(((pix shr 8) and 0xFF) / 255f); buf.putFloat((pix and 0xFF) / 255f)
        }
        return buf
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

