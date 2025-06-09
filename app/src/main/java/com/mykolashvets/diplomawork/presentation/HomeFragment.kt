package com.mykolashvets.diplomawork.presentation


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.exifinterface.media.ExifInterface
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.mykolashvets.diplomawork.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val vm by viewModels<HomeViewModel>()

    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var pickLauncher: ActivityResultLauncher<String>

    private val CAMERA_PERMS = arrayOf(Manifest.permission.CAMERA)
    private val STORAGE_PERMS = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, inset ->
            val bars = inset.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            inset
        }
        pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            stopCamera()
            viewLifecycleOwner.lifecycleScope.launch {
                loadBitmap(uri)?.let { vm.classifyBitmap(it) }
            }
        }
        b.btnLiveScan.setOnClickListener {
            if (hasPerms(CAMERA_PERMS)) startCamera() else requestPermissions(CAMERA_PERMS, 100)
        }
        b.btnGalleryPick.setOnClickListener {
            stopCamera()
            if (hasPerms(STORAGE_PERMS)) pickLauncher.launch("image/*") else requestPermissions(STORAGE_PERMS, 101)
        }
        vm.result.observe(viewLifecycleOwner) {
            b.resultText.text = "${it.label}: ${(it.confidence * 100).toInt()}%";
            b.resultText.visibility = View.VISIBLE
        }
    }

    private fun hasPerms(perms: Array<String>) = perms.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(code: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        when (code) {
            100 -> if (hasPerms(CAMERA_PERMS)) startCamera()
            101 -> if (hasPerms(STORAGE_PERMS)) pickLauncher.launch("image/*")
        }
    }

    // CameraX launch
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            cameraProvider = future.get()
            val preview = Preview.Builder().build().apply { setSurfaceProvider(b.previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { proxy ->
                    proxy.toBitmap()?.let { vm.classifyBitmap(it) }; proxy.close()
                }
            }
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    private fun stopCamera() { cameraProvider?.unbindAll() }

    private fun ImageProxy.toBitmap(): Bitmap? = try {
        val y = planes[0].buffer; val u = planes[1].buffer; val v = planes[2].buffer
        val bytes = ByteArray(y.remaining() + u.remaining() + v.remaining())
        y.get(bytes, 0, y.remaining()); v.get(bytes, y.remaining(), v.remaining()); u.get(bytes, y.remaining() + v.remaining(), u.remaining())
        val yuv = YuvImage(bytes, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
        BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    } catch (e: Exception) { Log.e("HomeFrag", "proxy→bmp", e); null }

    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inSampleSize = 2 }
            val raw = requireContext().contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            val rot = if (Build.VERSION.SDK_INT >= 24) requireContext().contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } else 0
            if (rot != 0) Bitmap.createBitmap(raw!!, 0, 0, raw.width, raw.height, Matrix().apply {
                if (rot != null) {
                    postRotate(rot.toFloat())
                }
            }, true) else raw
        }.getOrNull()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null; stopCamera() }
}
