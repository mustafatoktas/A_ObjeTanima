package com.mustafatoktas.objetanima.ui.ana

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mustafatoktas.objetanima.R
import com.mustafatoktas.objetanima.databinding.FragmentAnaBinding
import com.mustafatoktas.objetanima.ml.AutoModel1
import com.mustafatoktas.objetanima.model.TaninanObjeler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import android.view.Surface
import androidx.fragment.app.viewModels

class AnaFragment : Fragment() {

    private lateinit var etiketler:List<String>
    private var renklerListesi = listOf(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    private val paint = Paint()
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var model : AutoModel1
    private lateinit var handler : Handler
    private lateinit var kameraYonetisi : CameraManager
    private var kameraAygiti: CameraDevice? = null
    private lateinit var coroutineScope: CoroutineScope
    private val taninanObjeler = mutableListOf<TaninanObjeler>()
    private lateinit var gucYonetimi: PowerManager
    private lateinit var uyaniklikKilidi: PowerManager.WakeLock
    private val vm: AnaViewModel by viewModels()
    private var _binding : FragmentAnaBinding? = null
    private val b get() = _binding!!
    private var kamera = false
    private var tarama = false


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnaBinding.inflate(inflater,container,false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etiketler = FileUtil.loadLabels(requireContext(), "labels.txt")

        kameraYonetisi = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        coroutineScope = lifecycleScope

        coroutineScope.launch {
            uyanikTutmaKilidi()

            val handlerThread = HandlerThread("videoThread")
            handlerThread.start()
            handler = Handler(handlerThread.looper)

            imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
            model = AutoModel1.newInstance(requireContext())

            toolbarMenuTiklamaIslemleri()
            liveDatalariGozlemle()
        }
    }

    private fun liveDatalariGozlemle () {
        vm.taninanObjelerListesiLiveData.observe(viewLifecycleOwner) {
            val stringBuilder = StringBuilder()

            taninanObjeler.forEach {
                stringBuilder.append("${it.label}: %${it.olasilik.toInt()}\n")
            }
            b.multiLineTextView.text = stringBuilder.toString()
        }
    }

    private fun toolbarMenuTiklamaIslemleri (){
        b.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {

                R.id.kamerayiAc_menuitem -> {

                    kamera = kamera == false
                    kameraOperasyonlari(kamera)
                    return@setOnMenuItemClickListener true
                }

                R.id.tara_menuitem -> {
                    if (kamera) {
                        tarama = tarama == false
                        taramaOperasyonlari(tarama)
                    } else
                        Snackbar.make(requireView(),"Önce kamera açılmalı",Snackbar.LENGTH_SHORT).show()

                    return@setOnMenuItemClickListener true
                }

                else -> return@setOnMenuItemClickListener false
            }
        }
    }

    fun kameraOperasyonlari (kamera : Boolean) {
        if(kamera) {
            if (kameraIzniVarMi()) {
                kamerayiAc()
                b.txtKameraDurumu.setText(R.string.acik)
                b.txtKameraDurumu.setTextColor(Color.GREEN)
            } else kameraIzniIste()
        }
        else {
            tarama = false
            taramaOperasyonlari(false)
            kamerayiKapat()
            vm.taninanObjelerListesiLiveData.value = arrayListOf()
            b.txtKameraDurumu.setText(R.string.kapali)
            b.txtKameraDurumu.setTextColor(Color.RED)
        }
    }

    fun taramaOperasyonlari (tarama : Boolean) {
        if(tarama && kamera ) {
            b.txtObjeTanimaDurumu.setText(R.string.acik)
            b.txtObjeTanimaDurumu.setTextColor(Color.GREEN)
            textureIslemleri()
        }
        else {
            b.txtObjeTanimaDurumu.setText(R.string.kapali)
            b.txtObjeTanimaDurumu.setTextColor(Color.RED)
        }
    }


    fun kameraIzniVarMi () = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED


    fun kameraIzniIste () {

        @Suppress("DEPRECATION")
        requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 255)
    }


    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult (requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode) {
            255 -> {
                if(kameraIzniVarMi()){
                    kamerayiAc()
                    b.txtKameraDurumu.setText(R.string.acik)
                    b.txtKameraDurumu.setTextColor(Color.GREEN)
                }
                else Snackbar.make(requireView(), "İzin alınamadı", Snackbar.LENGTH_SHORT).show()
            }
        }
    }


    private fun uyanikTutmaKilidi() {
        gucYonetimi = requireActivity().getSystemService(Context.POWER_SERVICE) as PowerManager

        @Suppress("DEPRECATION")
        uyaniklikKilidi = gucYonetimi.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "ObjetAnima::UyanikTutmaKilidimTag")

        uyaniklikKilidi.acquire(10*60*1000L /*10 dakika*/)
    }


    fun textureIslemleri () {
        b.textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                coroutineScope.launch {
                    if (kamera) kamerayiAc()
                }
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

                coroutineScope.launch {
                        val bitmap = b.textureView.bitmap!!

                        var image = TensorImage.fromBitmap(bitmap)
                        image = imageProcessor.process(image)

                        val outputs = model.process(image)
                        val konumlar = outputs.locationsAsTensorBuffer.floatArray
                        val siniflar = outputs.classesAsTensorBuffer.floatArray
                        val skorlar = outputs.scoresAsTensorBuffer.floatArray
                        val mutable =  bitmap.copy(Bitmap.Config.ARGB_8888,true)
                        val tuval = Canvas(mutable)

                        val h = mutable.height
                        val w = mutable.width
                        paint.textSize = h/15f
                        paint.strokeWidth = h/85f

                        var x = 0

                        skorlar.forEachIndexed { index, fl ->
                            x = index
                            x *= 4
                            if (fl > 0.5 && tarama) {
                                val etiket = etiketler.get(siniflar.get(index).toInt())
                                val olasilik = fl * 100
                                val renk = renklerListesi.get(index)

                                taninanObjeler.add(TaninanObjeler(etiket, olasilik, renk))

                                paint.setColor(renk)
                                paint.style = Paint.Style.STROKE
                                tuval.drawRect(
                                    RectF(
                                        konumlar.get(x + 1) * w,
                                        konumlar.get(x) * h,
                                        konumlar.get(x + 3) * w,
                                        konumlar.get(x + 2) * h
                                    ), paint
                                )
                                paint.style = Paint.Style.FILL
                                tuval.drawText(
                                    "${etiket}",
                                    konumlar.get(x + 1) * w,
                                    konumlar.get(x) * h,
                                    paint
                                )
                            }
                        }
                        b.imageView.setImageBitmap(mutable)
                        vm.taninanObjelerListesiLiveData.value = taninanObjeler
                        taninanObjeler.clear()
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    fun kamerayiAc () {
        coroutineScope.launch {

            b.textureView.visibility = View.VISIBLE
            b.imageView.visibility = View.VISIBLE
            kameraYonetisi.openCamera(kameraYonetisi.cameraIdList[0], object : CameraDevice.StateCallback() {

                override fun onDisconnected(camera: CameraDevice) {}
                override fun onError(camera: CameraDevice, error: Int) {}

                override fun onOpened(camera: CameraDevice) {

                        kameraAygiti = camera

                        val surface = Surface(b.textureView.surfaceTexture)
                        val yakalamaIstegi = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        yakalamaIstegi.addTarget(surface)

                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){

                        override fun onConfigureFailed(session: CameraCaptureSession) {}

                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(yakalamaIstegi.build(), null , null)
                        }
                    }, handler)
                }
            } , handler)
        }
    }


    fun kamerayiKapat() {
        kameraAygiti?.close()
        kameraAygiti = null
        b.textureView.visibility = View.INVISIBLE
        b.imageView.visibility = View.INVISIBLE
    }


    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.launch {
            model.close()
            if (uyaniklikKilidi.isHeld) uyaniklikKilidi.release()
        }
    }
}