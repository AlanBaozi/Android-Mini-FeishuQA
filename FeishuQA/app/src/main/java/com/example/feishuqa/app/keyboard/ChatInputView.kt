package com.example.feishuqa.app.keyboard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
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
) : LinearLayout(context, attrs) {

    // 核心 UI
    private var etInput: EditText
    private var btnAttach: ImageButton
    private var btnToggleInput: ImageButton
    private var btnVoiceInput: TextView
    private var btnSend: ImageButton

    // 头部新功能
    private var btnWebSearch: View
    private var btnModelSelect: View
    private var tvSelectedModel: TextView

    // 图片预览 UI
    private var layoutPreview: View? = null
    private var ivPreview: ImageView? = null
    private var btnDeletePreview: View? = null
    private var viewPreviewMask: View? = null
    private var pbPreviewLoading: ProgressBar? = null

    // 逻辑控制
    private lateinit var viewModel: ChatInputViewModel
    private var actionListener: ActionListener? = null
    private var isVoiceMode = false

    // Baidu ASR 相关
    private lateinit var baiduAsrManager: BaiduAsrManager
    private val speechBuffer = StringBuilder()
    private var currentStreamText = ""
    private var isRecording = false
    private var isCanceling = false
    private var startTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    // Dialog 相关
    private var voiceDialog: Dialog? = null
    private var barWaveView: BarWaveView? = null
    private var tvStreamingText: TextView? = null
    private var tvHintState: TextView? = null
    private var bubbleContainer: View? = null

    // ★★★ 新增：图片全屏预览 Dialog
    private var imagePreviewDialog: Dialog? = null

    interface ActionListener {
        fun requestRecordAudioPermission(): Boolean
        fun openImagePicker()
        // fun onPreviewImageClick(uri: Any) // 如果不需要外部跳转 Activity，这个接口方法可以去掉了
        fun onWebSearchClick()
        fun onModelSelectClick()
    }

    init {
        orientation = VERTICAL
        val view = LayoutInflater.from(context).inflate(R.layout.layout_input_bar, this, true)

        // 1. 绑定基础控件
        etInput = view.findViewById(R.id.et_input)
        btnAttach = view.findViewById(R.id.btn_attach)
        btnToggleInput = view.findViewById(R.id.btn_toggle_input_mode)
        btnVoiceInput = view.findViewById(R.id.btn_voice_input)
        btnSend = view.findViewById(R.id.btn_send)

        btnWebSearch = view.findViewById(R.id.btn_web_search)
        btnModelSelect = view.findViewById(R.id.btn_model_select)
        tvSelectedModel = view.findViewById(R.id.tv_selected_model)

        // 2. 绑定预览控件
        layoutPreview = view.findViewById(R.id.layoutPreview)
        ivPreview = view.findViewById(R.id.ivPreview)
        btnDeletePreview = view.findViewById(R.id.btnDeletePreview)
        viewPreviewMask = view.findViewById(R.id.layoutPreviewLoading)
        pbPreviewLoading = view.findViewById(R.id.pbPreviewLoading)

        setupListeners()
    }

    fun setActionListener(listener: ActionListener) {
        this.actionListener = listener
    }

    fun init(owner: ViewModelStoreOwner, factory: ViewModelProvider.Factory) {
        if (owner !is LifecycleOwner) throw IllegalArgumentException("Owner must be LifecycleOwner")

        viewModel = ViewModelProvider(owner, factory)[ChatInputViewModel::class.java]
        viewModel.pendingImageUri.observe(owner) { uri -> handleImagePreview(uri) }
        initBaiduAsr(context.applicationContext)
    }

    fun onImageSelected(uri: android.net.Uri) {
        viewModel.selectImage(uri)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (::baiduAsrManager.isInitialized) baiduAsrManager.destroy()
        handler.removeCallbacksAndMessages(null)
        dismissVoiceDialog()
        // ★★★ 销毁图片预览弹窗
        dismissImagePreviewDialog()
    }

    private fun setupListeners() {
        // 发送逻辑
        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty() || (layoutPreview?.visibility == View.VISIBLE)) {
                viewModel.sendMessage(text)
                etInput.setText("")
            }
        }

        // 头部按钮
        btnWebSearch.setOnClickListener { actionListener?.onWebSearchClick() }
        btnModelSelect.setOnClickListener { actionListener?.onModelSelectClick() }

        // 图片逻辑
        btnAttach.setOnClickListener { actionListener?.openImagePicker() }
        btnDeletePreview?.setOnClickListener { viewModel.clearPendingImage() }

        // ★★★ 修改：点击小图直接在当前 View 弹出全屏预览
        ivPreview?.setOnClickListener {
            viewModel.pendingImageUri.value?.let { uri ->
                showImagePreviewDialog(uri)
            }
        }

        // 输入监听
        etInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState()
            }
        })

        // 模式切换
        btnToggleInput.setOnClickListener {
            toggleInputMode()
        }

        setupVoiceTouchListener()
    }

    // ★★★ 新增：显示全屏图片预览
    private fun showImagePreviewDialog(uri: Any) {
        if (imagePreviewDialog == null) {
            // 使用全屏无标题样式，背景纯黑
            imagePreviewDialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                setContentView(R.layout.dialog_image_preview)
                window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }

        val ivFull = imagePreviewDialog?.findViewById<ImageView>(R.id.ivFullImage)

        if (ivFull != null) {
            // 加载原图
            Glide.with(context)
                .load(uri)
                .fitCenter() // 保证图片完整显示
                .into(ivFull)

            // 点击大图关闭预览
            ivFull.setOnClickListener {
                imagePreviewDialog?.dismiss()
            }
        }

        // 安全显示
        if ((context as? android.app.Activity)?.isFinishing == false) {
            imagePreviewDialog?.show()
        }
    }

    // ★★★ 新增：安全关闭图片预览
    private fun dismissImagePreviewDialog() {
        if (imagePreviewDialog?.isShowing == true && (context as? android.app.Activity)?.isFinishing == false) {
            try { imagePreviewDialog?.dismiss() } catch (_: Exception) {}
        }
        imagePreviewDialog = null
    }

    private fun updateSendButtonState() {
        val hasText = etInput.text.toString().trim().isNotEmpty()
        val hasImage = layoutPreview?.visibility == View.VISIBLE
        btnSend.visibility = if (hasText || hasImage) View.VISIBLE else View.GONE
    }

    private fun toggleInputMode() {
        isVoiceMode = !isVoiceMode
        if (isVoiceMode) {
            etInput.visibility = View.GONE
            btnVoiceInput.visibility = View.VISIBLE
            btnToggleInput.setImageResource(R.drawable.ic_keyboard) // 请确保有这个资源
            hideKeyboard()
            btnSend.visibility = View.GONE
        } else {
            etInput.visibility = View.VISIBLE
            btnVoiceInput.visibility = View.GONE
            btnToggleInput.setImageResource(R.drawable.ic_mic)
            showKeyboard()
            updateSendButtonState()
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun showKeyboard() {
        etInput.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    // 处理图片预览 (小图)
    private fun handleImagePreview(uri: Any?) {
        val previewLayout = layoutPreview ?: return
        val imageView = ivPreview ?: return
        val mask = viewPreviewMask
        val pb = pbPreviewLoading

        if (uri != null) {
            previewLayout.visibility = View.VISIBLE
            mask?.visibility = View.VISIBLE
            pb?.visibility = View.VISIBLE
            updateSendButtonState()

            Glide.with(this).load(uri).centerCrop()
                .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        mask?.visibility = View.GONE
                        pb?.visibility = View.GONE
                        return false
                    }
                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        handler.postDelayed({
                            mask?.visibility = View.GONE
                            pb?.visibility = View.GONE
                        }, 300)
                        return false
                    }
                }).into(imageView)
        } else {
            previewLayout.visibility = View.GONE
            updateSendButtonState()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceTouchListener() {
        btnVoiceInput.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btnVoiceInput.text = "松开 发送"
                    btnVoiceInput.alpha = 0.7f
                    if (actionListener?.requestRecordAudioPermission() == true) startRecording()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isRecording) updateCancelState(event.y < -100)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    btnVoiceInput.text = "按住 说话"
                    btnVoiceInput.alpha = 1.0f
                    if (isCanceling) stopRecording(true) else {
                        barWaveView?.updateVolume(0f)
                        handler.postDelayed({ stopRecording(false) }, 100)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    btnVoiceInput.text = "按住 说话"
                    btnVoiceInput.alpha = 1.0f
                    stopRecording(true)
                    true
                }
                else -> false
            }
        }
    }

    // ASR 逻辑
    private fun initBaiduAsr(appContext: Context) {
        baiduAsrManager = BaiduAsrManager(appContext, object : BaiduAsrManager.AsrListener {
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
            if (System.currentTimeMillis() - startTime < 800) {
                handler.postDelayed({ dismissVoiceDialog() }, 800)
            } else dismissVoiceDialog()
            baiduAsrManager.cancel(); return
        }
        baiduAsrManager.stop()
    }

    private fun commitResult() {
        val finalText = speechBuffer.toString()
        if (finalText.isNotEmpty()) {
            etInput.append(finalText)
            if(isVoiceMode) toggleInputMode()
        }
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