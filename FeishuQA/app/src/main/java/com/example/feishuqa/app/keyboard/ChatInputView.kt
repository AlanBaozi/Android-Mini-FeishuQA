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
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import com.example.feishuqa.R
import com.example.feishuqa.common.utils.BaiduAsrManager
import com.example.feishuqa.databinding.LayoutInputBarBinding
import com.example.feishuqa.databinding.DialogFeishuVoiceBinding
import com.example.feishuqa.databinding.DialogImagePreviewBinding

class ChatInputView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // 1. 核心 Binding (替换原有的 View 成员变量)
    // inflate(inflater, parent) 默认 attachToRoot=true，正好符合 merge 或 root 为 this 的情况
    private val binding: LayoutInputBarBinding =
        LayoutInputBarBinding.inflate(LayoutInflater.from(context), this,true)

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
    // 新增：持有 Voice Dialog 的 binding 引用，以便在回调中更新 UI
    private var voiceBinding: DialogFeishuVoiceBinding? = null

    // ★★★ 新增：图片全屏预览 Dialog
    private var imagePreviewDialog: Dialog? = null

    interface ActionListener {
        fun requestRecordAudioPermission(): Boolean
        fun openImagePicker()
        fun onWebSearchClick()
        fun onModelSelectClick()

        /**
         * 检查是否已登录（用于发送消息前的检查）
         */
        fun checkLoginBeforeSend(): Boolean = true
    }

    init {
        orientation = VERTICAL
        // Binding.inflate 已经完成了视图加载和绑定，此处不再需要 inflate 和 findViewById
        setupListeners()
    }

    fun updateModelName(name: String) {
        // 使用 binding 访问，不再需要 findViewById
        binding.tvSelectedModel.text = name
    }

    /**
     * 公开方法：更新联网搜索按钮的 UI 状态
     */
    fun updateWebSearchState(isEnabled: Boolean) {
        if (isEnabled) {
            binding.btnWebSearch.setBackgroundResource(R.drawable.bg_chip_selected)
            binding.tvWebSearch.setTextColor(context.getColor(R.color.feishu_blue))
            binding.icWebSearch.setColorFilter(context.getColor(R.color.feishu_blue))
        } else {
            binding.btnWebSearch.setBackgroundResource(R.drawable.bg_chip_normal)
            binding.tvWebSearch.setTextColor(context.getColor(R.color.text_secondary))
            binding.icWebSearch.setColorFilter(context.getColor(R.color.text_secondary))
        }
    }

    /**
     * 公开方法：设置输入框内容并自动弹出键盘 (用于点击推荐问题)
     */
    fun setInputTextAndFocus(content: String) {
        binding.etInput.setText(content)
        binding.etInput.setSelection(content.length)
        showKeyboard() // 调用内部已有的 showKeyboard 方法
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

    /**
     * 清空输入框草稿内容（跳转界面时调用）
     */
    fun clearDraft() {
        binding.etInput.setText("")
        viewModel.clearPendingImage()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (::baiduAsrManager.isInitialized) baiduAsrManager.destroy()
        handler.removeCallbacksAndMessages(null)
        dismissVoiceDialog()
        dismissImagePreviewDialog()
    }

    private fun setupListeners() {
        // 发送逻辑
        binding.btnSend.setOnClickListener {
            if (actionListener?.checkLoginBeforeSend() == false) {
                return@setOnClickListener
            }

            val text = binding.etInput.text.toString().trim()
            if (text.isNotEmpty() || (binding.layoutPreview.visibility == View.VISIBLE)) {
                viewModel.sendMessage(text)
                binding.etInput.setText("")
            }
        }

        // 头部按钮
        binding.btnWebSearch.setOnClickListener { actionListener?.onWebSearchClick() }
        binding.btnModelSelect.setOnClickListener { actionListener?.onModelSelectClick() }

        // 图片逻辑
        binding.btnAttach.setOnClickListener { actionListener?.openImagePicker() }
        binding.btnDeletePreview.setOnClickListener { viewModel.clearPendingImage() }

        // 小图预览点击
        binding.ivPreview.setOnClickListener {
            viewModel.pendingImageUri.value?.let { uri ->
                showImagePreviewDialog(uri)
            }
        }

        // 输入监听
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState()
            }
        })

        // 模式切换
        binding.btnToggleInputMode.setOnClickListener {
            toggleInputMode()
        }

        setupVoiceTouchListener()
    }

    // ★★★ 新增：显示全屏图片预览
    private fun showImagePreviewDialog(uri: Any) {
        if (imagePreviewDialog == null) {
            imagePreviewDialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        }

        // 使用 Binding 加载 Dialog 布局
        val dialogBinding = DialogImagePreviewBinding.inflate(LayoutInflater.from(context))
        imagePreviewDialog?.setContentView(dialogBinding.root)
        imagePreviewDialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // 加载原图
        Glide.with(context)
            .load(uri)
            .fitCenter()
            .into(dialogBinding.ivFullImage) // 假设 layout xml 中 id 为 ivFullImage

        // 点击大图关闭预览
        dialogBinding.ivFullImage.setOnClickListener {
            imagePreviewDialog?.dismiss()
        }

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
        val hasText = binding.etInput.text.toString().trim().isNotEmpty()
        val hasImage = binding.layoutPreview.visibility == View.VISIBLE
        binding.btnSend.visibility = if (hasText || hasImage) View.VISIBLE else View.GONE
    }

    private fun toggleInputMode() {
        isVoiceMode = !isVoiceMode
        if (isVoiceMode) {
            binding.etInput.visibility = View.GONE
            binding.btnVoiceInput.visibility = View.VISIBLE
            binding.btnToggleInputMode.setImageResource(R.drawable.ic_keyboard)
            hideKeyboard()
            binding.btnSend.visibility = View.GONE
        } else {
            binding.etInput.visibility = View.VISIBLE
            binding.btnVoiceInput.visibility = View.GONE
            binding.btnToggleInputMode.setImageResource(R.drawable.ic_mic)
            showKeyboard()
            updateSendButtonState()
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun showKeyboard() {
        binding.etInput.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    // 处理图片预览 (小图)
    private fun handleImagePreview(uri: Any?) {
        if (uri != null) {
            binding.layoutPreview.visibility = View.VISIBLE
            binding.layoutPreviewLoading.visibility = View.VISIBLE // 对应 viewPreviewMask
            binding.pbPreviewLoading.visibility = View.VISIBLE
            updateSendButtonState()

            Glide.with(this).load(uri).centerCrop()
                .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        binding.layoutPreviewLoading.visibility = View.GONE
                        binding.pbPreviewLoading.visibility = View.GONE
                        return false
                    }
                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        handler.postDelayed({
                            binding.layoutPreviewLoading.visibility = View.GONE
                            binding.pbPreviewLoading.visibility = View.GONE
                        }, 300)
                        return false
                    }
                }).into(binding.ivPreview)
        } else {
            binding.layoutPreview.visibility = View.GONE
            updateSendButtonState()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceTouchListener() {
        binding.btnVoiceInput.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.btnVoiceInput.text = "松开 发送"
                    binding.btnVoiceInput.alpha = 0.7f
                    if (actionListener?.requestRecordAudioPermission() == true) startRecording()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isRecording) updateCancelState(event.y < -100)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    binding.btnVoiceInput.text = "按住 说话"
                    binding.btnVoiceInput.alpha = 1.0f
                    if (isCanceling) stopRecording(true) else {
                        voiceBinding?.barWaveView?.updateVolume(0f)
                        handler.postDelayed({ stopRecording(false) }, 100)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    binding.btnVoiceInput.text = "按住 说话"
                    binding.btnVoiceInput.alpha = 1.0f
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
            override fun onVolumeChanged(volumePercent: Float) {
                if (isRecording) voiceBinding?.barWaveView?.updateVolume(volumePercent / 2)
            }
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
        isRecording = false; voiceBinding?.barWaveView?.updateVolume(0f)
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
            binding.etInput.append(finalText)
            if(isVoiceMode) toggleInputMode()
        }
        resetVoiceState(); handler.postDelayed({ dismissVoiceDialog() }, 150)
    }

    private fun resetVoiceState() { speechBuffer.clear(); currentStreamText = ""; dismissVoiceDialog() }

    private fun updateDisplayText() {
        voiceBinding?.tvStreamingText?.text = "$speechBuffer$currentStreamText".ifEmpty { "..." }
    }

    private fun updateCancelState(cancel: Boolean) {
        if (isCanceling == cancel) return
        isCanceling = cancel
        if (isCanceling) {
            voiceBinding?.tvHintState?.text = "松开手指，取消发送"
            voiceBinding?.tvHintState?.setTextColor(Color.RED)
            voiceBinding?.bubbleContainer?.alpha = 0.5f
        } else {
            voiceBinding?.tvHintState?.text = "松开转文字，上滑取消"
            voiceBinding?.tvHintState?.setTextColor(Color.parseColor("#999999"))
            voiceBinding?.bubbleContainer?.alpha = 1.0f
        }
    }

    private fun showVoiceDialog() {
        if (voiceDialog == null) {
            voiceDialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
                setCancelable(false)
            }
        }

        // 使用 Binding 加载 Dialog 视图
        if (voiceBinding == null) {
            voiceBinding = DialogFeishuVoiceBinding.inflate(LayoutInflater.from(context))
            voiceDialog?.setContentView(voiceBinding!!.root)
        }

        isCanceling = false
        voiceBinding?.apply {
            tvHintState.text = "松开转文字，上滑取消"
            tvHintState.setTextColor(Color.parseColor("#999999"))
            tvStreamingText.text = ""
            bubbleContainer.alpha = 1.0f
            barWaveView.updateVolume(0f)
        }

        if ((context as? android.app.Activity)?.isFinishing == false) voiceDialog?.show()
    }

    private fun dismissVoiceDialog() {
        if (voiceDialog?.isShowing == true && (context as? android.app.Activity)?.isFinishing == false) {
            try { voiceDialog?.dismiss() } catch (_: Exception) {}
        }
    }
}