package com.example.testapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket

class MainActivity : AppCompatActivity() {
    // 控件对象
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var etAngle: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnSend: Button
    private lateinit var tvStatus: TextView

    // TCP相关变量
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper()) // 主线程更新UI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定控件
        etIp = findViewById(R.id.et_ip)
        etPort = findViewById(R.id.et_port)
        etAngle = findViewById(R.id.et_angle)
        btnConnect = findViewById(R.id.btn_connect)
        btnSend = findViewById(R.id.btn_send)
        tvStatus = findViewById(R.id.tv_status)

        // 连接/断开按钮点击事件
        btnConnect.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                val ip = etIp.text.toString().trim()
                val port = etPort.text.toString().trim().toIntOrNull() ?: 8888
                connectToRaspberryPi(ip, port)
            }
        }

        // 发送指令按钮点击事件
        btnSend.setOnClickListener {
//            if (!isConnected) {
//                Toast.makeText(this, "请先连接树莓派！", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
            if (!isConnected) {
//                disconnect()
                val ip = etIp.text.toString().trim()
                val port = etPort.text.toString().trim().toIntOrNull() ?: 8888
                connectToRaspberryPi(ip, port)
            }
            val angle = etAngle.text.toString().trim()
            if (angle.isEmpty()) {
                Toast.makeText(this, "请输入角度值！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendCommand("angle=$angle")
        }
    }

    // 连接树莓派
    private fun connectToRaspberryPi(ip: String, port: Int) {
        Thread {
            try {
                socket = Socket(ip, port)
                outputStream = socket!!.getOutputStream()
                isConnected = true

                // 更新UI（主线程）
                handler.post {
                    btnConnect.text = "断开连接"
                    tvStatus.text = "已连接到 $ip:$port"
                    Toast.makeText(this, "连接成功！", Toast.LENGTH_SHORT).show()
                }

                // 监听树莓派返回的数据
                listenForResponse()
            } catch (e: Exception) {
                handler.post {
                    tvStatus.text = "连接失败：${e.message}"
                    Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // 监听树莓派返回的数据
    private fun listenForResponse() {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket!!.inputStream))
                var response: String?=null
                while (isConnected && (reader.readLine().also { response = it }) != null) {
                    val finalResponse = response
                    handler.post {
                        tvStatus.text = "树莓派返回：$finalResponse"
                    }
                }
            } catch (e: Exception) {
                if (isConnected) {
                    handler.post {
                        tvStatus.text = "连接已断开：${e.message}"
                        disconnect()
                    }
                }
            }
        }.start()
    }

    // 发送指令
    private fun sendCommand(command: String) {
        Thread {
            try {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()
                handler.post {
                    tvStatus.text = "已发送指令：$command"
                }
            } catch (e: Exception) {
                handler.post {
                    tvStatus.text = "发送失败：${e.message}"
                    disconnect()
                }
            }
        }.start()
    }

    // 断开连接
    private fun disconnect() {
        try {
            isConnected = false
            outputStream?.close()
            socket?.close()
            handler.post {
                btnConnect.text = "连接树莓派"
                tvStatus.text = "已断开连接"
            }
        } catch (e: Exception) {
            handler.post {
                tvStatus.text = "断开失败：${e.message}"
            }
        }
    }

    // 页面销毁时断开连接
    override fun onDestroy() {
        super.onDestroy()
        if (isConnected) {
            disconnect()
        }
    }
}