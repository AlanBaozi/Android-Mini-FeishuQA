package com.zjl.myapplication

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.feishuqa.R
import com.example.feishuqa.app.keyboard.ChatViewModelFactory
import com.example.feishuqa.data.repository.ChatRepositoryExample
import com.zjl.myapplication.test.ChatDisplayView
import com.zjl.myapplication.test.ChatInputView

class MainActivity : AppCompatActivity() {

    private lateinit var chatDisplayView: ChatDisplayView
    private lateinit var chatInputView: ChatInputView

    // 1. 获取 Repository 单例
    private val repository by lazy { ChatRepositoryExample.getInstance(applicationContext) }
    // 2. 创建 ViewModel 工厂
    private val vmFactory by lazy { ChatViewModelFactory(repository) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(this, "需录音权限", Toast.LENGTH_SHORT).show()
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) chatInputView.onImageSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_activity_example)

        chatDisplayView = findViewById(R.id.chatDisplayView)
        chatInputView = findViewById(R.id.chatInputView)

        // 绑定 ViewModel
        chatDisplayView.init(this, vmFactory)
        chatInputView.init(this, vmFactory)

        // 设置回调
        chatDisplayView.onImageClick = { model -> showFullImageDialog(model) }

        chatInputView.setActionListener(object : ChatInputView.ActionListener {
            override fun requestRecordAudioPermission(): Boolean {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return false
            }
            override fun openImagePicker() { pickImageLauncher.launch("image/*") }
            override fun onPreviewImageClick(uri: Any) { showFullImageDialog(uri) }
        })
    }

    private fun showFullImageDialog(model: Any) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_preview)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.findViewById<ImageView>(R.id.ivFullImage).apply {
            setOnClickListener { dialog.dismiss() }
            Glide.with(this).load(model).fitCenter().into(this)
        }
        dialog.show()
    }
}