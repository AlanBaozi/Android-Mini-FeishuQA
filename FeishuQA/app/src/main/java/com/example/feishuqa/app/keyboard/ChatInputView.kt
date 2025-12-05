package com.zjl.myapplication.test

import android.annotation.SuppressLint
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import com.example.feishuqa.R
import com.example.feishuqa.app.keyboard.BarWaveView
import com.example.feishuqa.common.utils.BaiduAsrManager

class ChatInputView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    // UI
    private var etInput: EditText
    private var btnVoice: ImageView
    private var btnSend: ImageView
    private var btnImage: ImageView
    private var layoutPreview: View
    private var ivPreview: ImageView
    private var ivDeletePreview: ImageView
    private var viewPreviewMask: View
    private var pbPreviewLoading: ProgressBar

    // Logic
    private lateinit var viewModel: ChatInputViewModel
    private var actionListener: ActionListener? = null

    // Baidu ASR
    private lateinit var baiduAsrManager: BaiduAsrManager
    private val speechBuffer = StringBuilder()
    private var currentStreamText = ""
    private var isRecording = false
    private var isCanceling = false
    private var startTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    // Dialog
    private var voiceDialog: Dialog? = null
    private var barWaveView: BarWaveView? = null
    private var tvStreamingText: TextView? = null
    private var tvHintState: TextView? = null
    private var bubbleContainer: View? = null

    interface ActionListener {
        fun requestRecordAudioPermission(): Boolean
        fun openImagePicker()
        fun onPreviewImageClick(uri: Any)
    }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_chat_input, this, true)
        etInput = view.findViewById(R.id.etInput)
        btnVoice = view.findViewById(R.id.btnVoice)
        btnSend = view.findViewById(R.id.btnSend)
        btnImage = view.findViewById(R.id.btnImage)
        layoutPreview = view.findViewById(R.id.layoutPreview)
        ivPreview = view.findViewById(R.id.ivPreview)
        ivDeletePreview = view.findViewById(R.id.ivDeletePreview)
        viewPreviewMask = view.findViewById(R.id.viewPreviewMask)
        pbPreviewLoading = view.findViewById(R.id.pbPreviewLoading)
        setupListeners()
    }

    fun setActionListener(listener: ActionListener) {
        this.actionListener = listener
    }

    // 初始化：传入 Factory
    fun init(owner: ViewModelStoreOwner, factory: ViewModelProvider.Factory) {
        if (owner !is LifecycleOwner) throw IllegalArgumentException("Owner must be LifecycleOwner")

        // 使用 Factory 创建 VM
        viewModel = ViewModelProvider(owner, factory)[ChatInputViewModel::class.java]

        viewModel.pendingImageUri.observe(owner) { uri -> handleImagePreview(uri) }
        initBaiduAsr()
    }

    fun onImageSelected(uri: android.net.Uri) {
        viewModel.selectImage(uri)
    }

    // 自动清理
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (::baiduAsrManager.isInitialized) baiduAsrManager.destroy()
        handler.removeCallbacksAndMessages(null)
        if (voiceDialog?.isShowing == true) voiceDialog?.dismiss()
        voiceDialog = null
    }

    private fun setupListeners() {
        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            viewModel.sendMessage(text)
            etInput.setText("")
        }
        btnImage.setOnClickListener { actionListener?.openImagePicker() }
        ivDeletePreview.setOnClickListener { viewModel.clearPendingImage() }
        ivPreview.setOnClickListener { viewModel.pendingImageUri.value?.let { actionListener?.onPreviewImageClick(it) } }
        setupVoiceTouchListener()
    }

    // 图片预览逻辑
    private fun handleImagePreview(uri: Any?) {
        if (uri != null) {
            layoutPreview.visibility = View.VISIBLE
            viewPreviewMask.visibility = View.VISIBLE
            pbPreviewLoading.visibility = View.VISIBLE
            Glide.with(this).load(uri).centerCrop()
                .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        viewPreviewMask.visibility = View.GONE
                        pbPreviewLoading.visibility = View.GONE
                        return false
                    }
                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        handler.postDelayed({
                            viewPreviewMask.visibility = View.GONE
                            pbPreviewLoading.visibility = View.GONE
                        }, 300)
                        return false
                    }
                }).into(ivPreview)
        } else {
            layoutPreview.visibility = View.GONE
        }
    }

    // 语音触摸逻辑
    @SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceTouchListener() {
        btnVoice.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (actionListener?.requestRecordAudioPermission() == true) startRecording()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isRecording) updateCancelState(event.y < -100)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isCanceling) stopRecording(true) else {
                        barWaveView?.updateVolume(0f)
                        handler.postDelayed({ stopRecording(false) }, 100)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { stopRecording(true); true }
                else -> false
            }
        }
    }

    private fun initBaiduAsr() {
        // 【Context安全】使用 ApplicationContext 防止泄漏
        baiduAsrManager = BaiduAsrManager(context.applicationContext, object : BaiduAsrManager.AsrListener {
            override fun onReady() = updateDisplayText()
            override fun onVolumeChanged(volumePercent: Float) { if (isRecording) barWaveView?.updateVolume(volumePercent / 2) }
            override fun onPartialResult(text: String) { currentStreamText = text; updateDisplayText() }
            override fun onFinalResult(text: String) { speechBuffer.append(text); currentStreamText = "" }
            override fun onFinish() {
                if (isCanceling) { resetVoiceState(); return }
                if (isRecording) baiduAsrManager.start() else commitResult()
            }
            override fun onError(errorCode: Int, message: String) {
                if (isRecording) {
                    if (errorCode == 7001 || errorCode == 6001 || errorCode == 2000) {
                        baiduAsrManager.cancel(); baiduAsrManager.start(); return
                    }
                    tvHintState?.text = "错误: $errorCode"; tvHintState?.setTextColor(Color.RED)
                }
            }
        })
    }

    private fun startRecording() {
        isRecording = true; isCanceling = false; startTime = System.currentTimeMillis()
        speechBuffer.clear(); currentStreamText = ""
        showVoiceDialog(); baiduAsrManager.start()
    }

    private fun stopRecording(cancel: Boolean) {
        isRecording = false; barWaveView?.updateVolume(0f)
        if (cancel) { isCanceling = true; resetVoiceState(); baiduAsrManager.cancel(); return }
        if (speechBuffer.isEmpty() && currentStreamText.isEmpty()) {
            if (System.currentTimeMillis() - startTime < 800) handler.postDelayed({ dismissVoiceDialog() }, 800)
            else dismissVoiceDialog()
            baiduAsrManager.cancel(); return
        }
        baiduAsrManager.stop()
    }

    private fun commitResult() {
        val finalText = speechBuffer.toString()
        if (finalText.isNotEmpty()) etInput.append(finalText)
        resetVoiceState(); handler.postDelayed({ dismissVoiceDialog() }, 150)
    }

    private fun resetVoiceState() { speechBuffer.clear(); currentStreamText = ""; dismissVoiceDialog() }
    private fun updateDisplayText() { tvStreamingText?.text = "$speechBuffer$currentStreamText".ifEmpty { "..." } }

    private fun updateCancelState(cancel: Boolean) {
        if (isCanceling == cancel) return
        isCanceling = cancel
        if (isCanceling) {
            tvHintState?.text = "松开手指，取消发送"; tvHintState?.setTextColor(Color.RED); bubbleContainer?.alpha = 0.5f
        } else {
            tvHintState?.text = "松开转文字，上滑取消"; tvHintState?.setTextColor(Color.parseColor("#999999")); bubbleContainer?.alpha = 1.0f
        }
    }

    private fun showVoiceDialog() {
        if (voiceDialog == null) {
            // 【UI安全】Dialog 必须使用 Activity Context
            voiceDialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
                setContentView(R.layout.dialog_feishu_voice)
                setCancelable(false)
            }
            barWaveView = voiceDialog?.findViewById(R.id.barWaveView)
            tvStreamingText = voiceDialog?.findViewById(R.id.tvStreamingText)
            tvHintState = voiceDialog?.findViewById(R.id.tvHintState)
            bubbleContainer = voiceDialog?.findViewById(R.id.bubbleContainer)
        }
        isCanceling = false
        tvHintState?.text = "松开转文字，上滑取消"; tvHintState?.setTextColor(Color.parseColor("#999999"))
        tvStreamingText?.text = ""; bubbleContainer?.alpha = 1.0f; barWaveView?.updateVolume(0f)
        if ((context as? android.app.Activity)?.isFinishing == false) voiceDialog?.show()
    }

    private fun dismissVoiceDialog() {
        if (voiceDialog?.isShowing == true && (context as? android.app.Activity)?.isFinishing == false) {
            try { voiceDialog?.dismiss() } catch (_: Exception) {}
        }
    }
}