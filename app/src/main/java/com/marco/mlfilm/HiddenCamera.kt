package com.marco.mlfilm

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface

/** Camera aperta direttamente con Camera2 (per le lenti che CameraX non vede): anteprima sul renderer e scatto JPEG. */
class HiddenCamera(private val act: Activity, private val id: String, private val macro: Boolean) {
    private val thread = HandlerThread("lens-$id").apply { start() }
    private val h = Handler(thread.looper)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var surface: Surface? = null
    @Volatile var rotation = 90
        private set
    @Volatile private var closed = false
    private var afMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
    private var previewReq: CaptureRequest.Builder? = null
    private var active: Rect? = null
    private var maxZoom = 1f
    @Volatile var zoom = 1f
        private set
    private var focusRect: MeteringRectangle? = null

    private fun crop(): Rect? {
        val a = active ?: return null
        if (zoom <= 1.001f) return a
        val w = (a.width() / zoom).toInt()
        val hh = (a.height() / zoom).toInt()
        val x = a.left + (a.width() - w) / 2
        val y = a.top + (a.height() - hh) / 2
        return Rect(x, y, x + w, y + hh)
    }

    private fun applyTo(b: CaptureRequest.Builder) {
        crop()?.let { b.set(CaptureRequest.SCALER_CROP_REGION, it) }
        focusRect?.let {
            b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(it))
            b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(it))
        }
    }

    private fun refresh() {
        val b = previewReq ?: return
        val s = session ?: return
        try {
            applyTo(b)
            s.setRepeatingRequest(b.build(), null, h)
        } catch (_: Exception) {
        }
    }

    /** Pizzico: moltiplica lo zoom (digitale, ritaglio del sensore). */
    fun zoomBy(f: Float) {
        zoom = (zoom * f).coerceIn(1f, maxZoom)
        h.post { refresh() }
    }

    /** Tocco: (px, py) da 0 a 1 nello spazio del sensore, origine in alto a sinistra. */
    fun focusAt(px: Float, py: Float) {
        h.post {
            val a = active ?: return@post
            val cr = crop() ?: a
            val cx = cr.left + (px * cr.width()).toInt()
            val cy = cr.top + (py * cr.height()).toInt()
            val half = (a.width() * 0.06f).toInt()
            val r = Rect(
                (cx - half).coerceIn(a.left, a.right - 1), (cy - half).coerceIn(a.top, a.bottom - 1),
                (cx + half).coerceIn(a.left + 1, a.right), (cy + half).coerceIn(a.top + 1, a.bottom),
            )
            focusRect = MeteringRectangle(r, MeteringRectangle.METERING_WEIGHT_MAX - 1)
            val b = previewReq ?: return@post
            val s = session ?: return@post
            try {
                applyTo(b)
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                s.capture(b.build(), null, h)
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                s.setRepeatingRequest(b.build(), null, h)
            } catch (_: Exception) {
            }
        }
    }

    private fun pick(sizes: Array<Size>?, maxW: Int): Size? {
        if (sizes == null || sizes.isEmpty()) return null
        val ok = sizes.filter { it.width * 3 == it.height * 4 && it.width <= maxW }
        return (if (ok.isNotEmpty()) ok else sizes.toList()).maxByOrNull { it.width.toLong() * it.height }
    }

    fun open(renderer: FilmRenderer, onFail: () -> Unit) {
        try {
            val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val c = cm.getCameraCharacteristics(id)
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: run { onFail(); return }
            val sensor = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            maxZoom = minOf(c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f, 8f)
            @Suppress("DEPRECATION")
            val disp = when (act.windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            rotation = (sensor - disp + 360) % 360
            val prev = pick(map.getOutputSizes(SurfaceTexture::class.java), 1600) ?: run { onFail(); return }
            val jpg = pick(map.getOutputSizes(ImageFormat.JPEG), 4096) ?: run { onFail(); return }
            val modes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            afMode = when {
                macro && modes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO) -> CaptureRequest.CONTROL_AF_MODE_MACRO
                modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                else -> CaptureRequest.CONTROL_AF_MODE_AUTO
            }
            val s = renderer.cameraSurface(prev.width, prev.height, rotation) ?: run { onFail(); return }
            surface = s
            val r = ImageReader.newInstance(jpg.width, jpg.height, ImageFormat.JPEG, 2)
            reader = r
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    if (closed) { d.close(); return }
                    device = d
                    try {
                        @Suppress("DEPRECATION")
                        d.createCaptureSession(listOf(s, r.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(sess: CameraCaptureSession) {
                                if (closed) { sess.close(); return }
                                session = sess
                                try {
                                    val rq = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    rq.addTarget(s)
                                    rq.set(CaptureRequest.CONTROL_AF_MODE, afMode)
                                    previewReq = rq
                                    sess.setRepeatingRequest(rq.build(), null, h)
                                } catch (e: Exception) { onFail() }
                            }
                            override fun onConfigureFailed(sess: CameraCaptureSession) { onFail() }
                        }, h)
                    } catch (e: Exception) { onFail() }
                }
                override fun onDisconnected(d: CameraDevice) { d.close(); if (!closed) onFail() }
                override fun onError(d: CameraDevice, e: Int) { d.close(); if (!closed) onFail() }
            }, h)
        } catch (e: Exception) {
            onFail()
        }
    }

    /** Scatta un JPEG: callback con i byte e la rotazione da applicare, oppure null. */
    fun capture(onJpeg: (ByteArray?, Int) -> Unit) {
        val d = device
        val sess = session
        val r = reader
        if (d == null || sess == null || r == null || closed) { onJpeg(null, 0); return }
        try {
            r.setOnImageAvailableListener({ rd ->
                val img = try { rd.acquireLatestImage() } catch (e: Exception) { null }
                if (img == null) { onJpeg(null, 0); return@setOnImageAvailableListener }
                val bytes = try {
                    val b = img.planes[0].buffer
                    ByteArray(b.remaining()).also { b.get(it) }
                } catch (e: Exception) { null } finally { img.close() }
                onJpeg(bytes, rotation)
            }, h)
            val rq = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            rq.addTarget(r.surface)
            rq.set(CaptureRequest.CONTROL_AF_MODE, afMode)
            rq.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
            applyTo(rq)
            sess.capture(rq.build(), null, h)
        } catch (e: Exception) {
            onJpeg(null, 0)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        runCatching { surface?.release() }
        thread.quitSafely()
    }
}
