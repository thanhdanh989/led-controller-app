package com.danh.ledcontroller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.danh.ledcontroller.databinding.ActivityMainBinding
import com.danh.ledcontroller.databinding.DialogColorPickerBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity(), BleControllerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleController: BleController
    private lateinit var paletteAdapter: PaletteAdapter

    private val paletteColors: MutableList<IntArray> = mutableListOf(
        intArrayOf(255, 0, 0),
        intArrayOf(0, 255, 0),
        intArrayOf(0, 0, 255)
    )

    private var isConnected = false
    // Bat len khi dang tu dong cap nhat UI tu trang thai thiet bi, de khong gui nguoc lenh
    private var suppressSend = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            bleController.startScanAndConnect()
        } else {
            Toast.makeText(this, "Cần cấp quyền Bluetooth để kết nối", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleController = BleController(applicationContext, this)

        setupModeSpinner()
        setupPalette()
        setupSliders()
        setupButtons()
    }

    // ------------------------------------------------------------------
    private fun setupModeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, LED_MODES)
        binding.spinnerMode.adapter = adapter
        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (suppressSend) return
                bleController.sendCommand(BleProtocol.cmdMode(position))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupPalette() {
        binding.recyclerPalette.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        paletteAdapter = PaletteAdapter(paletteColors) { sendPaletteNow() }
        binding.recyclerPalette.adapter = paletteAdapter

        binding.btnAddColor.setOnClickListener {
            showColorPickerDialog(intArrayOf(255, 255, 255)) { rgb ->
                paletteAdapter.addColor(rgb)
            }
        }
    }

    private fun sendPaletteNow() {
        bleController.sendCommand(BleProtocol.cmdPalette(paletteColors))
    }

    private fun showColorPickerDialog(initial: IntArray, onConfirm: (IntArray) -> Unit) {
        val dialogBinding = DialogColorPickerBinding.inflate(LayoutInflater.from(this))
        dialogBinding.dialogSeekR.progress = initial[0]
        dialogBinding.dialogSeekG.progress = initial[1]
        dialogBinding.dialogSeekB.progress = initial[2]
        fun updatePreview() {
            dialogBinding.dialogPreview.setBackgroundColor(
                Color.rgb(dialogBinding.dialogSeekR.progress, dialogBinding.dialogSeekG.progress, dialogBinding.dialogSeekB.progress)
            )
        }
        updatePreview()
        val simpleListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        dialogBinding.dialogSeekR.setOnSeekBarChangeListener(simpleListener)
        dialogBinding.dialogSeekG.setOnSeekBarChangeListener(simpleListener)
        dialogBinding.dialogSeekB.setOnSeekBarChangeListener(simpleListener)

        AlertDialog.Builder(this)
            .setTitle("Chọn màu")
            .setView(dialogBinding.root)
            .setPositiveButton("Thêm") { _, _ ->
                onConfirm(intArrayOf(dialogBinding.dialogSeekR.progress, dialogBinding.dialogSeekG.progress, dialogBinding.dialogSeekB.progress))
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ------------------------------------------------------------------
    private fun setupSliders() {
        binding.switchPower.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSend) return@setOnCheckedChangeListener
            bleController.sendCommand(BleProtocol.cmdPower(isChecked))
        }

        attachSeek(binding.seekBrightness) { v -> bleController.sendCommand(BleProtocol.cmdBrightness(v)) }
        attachSeek(binding.seekSpeed) { v -> bleController.sendCommand(BleProtocol.cmdSpeed(v)) }

        val colorListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColorPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (suppressSend) return
                sendSolidColorNow()
            }
        }
        binding.seekR.setOnSeekBarChangeListener(colorListener)
        binding.seekG.setOnSeekBarChangeListener(colorListener)
        binding.seekB.setOnSeekBarChangeListener(colorListener)
        updateColorPreview()
    }

    private fun attachSeek(seek: SeekBar, onValue: (Int) -> Unit) {
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (suppressSend) return
                onValue(seekBar?.progress ?: return)
            }
        })
    }

    private fun updateColorPreview() {
        binding.colorPreview.setBackgroundColor(
            Color.rgb(binding.seekR.progress, binding.seekG.progress, binding.seekB.progress)
        )
    }

    private fun sendSolidColorNow() {
        bleController.sendCommand(BleProtocol.cmdColor(binding.seekR.progress, binding.seekG.progress, binding.seekB.progress))
    }

    // ------------------------------------------------------------------
    private fun setupButtons() {
        binding.btnConnect.setOnClickListener {
            if (isConnected) {
                bleController.disconnect()
                return@setOnClickListener
            }
            ensureBluetoothOnAndPermitted()
        }

        binding.btnApplyConfig.setOnClickListener {
            val count = binding.editLedCount.text.toString().toIntOrNull()
            val pin = binding.editGpioPin.text.toString().toIntOrNull()
            if (count != null && count in 1..600) {
                bleController.sendCommand(BleProtocol.cmdCount(count))
            } else if (binding.editLedCount.text.isNotEmpty()) {
                Toast.makeText(this, "Số lượng LED phải từ 1 đến 600", Toast.LENGTH_SHORT).show()
            }
            if (pin != null && pin in 0..21) {
                bleController.sendCommand(BleProtocol.cmdPin(pin))
            } else if (binding.editGpioPin.text.isNotEmpty()) {
                Toast.makeText(this, "Chân GPIO không hợp lệ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureBluetoothOnAndPermitted() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ Bluetooth", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Vui lòng bật Bluetooth trước", Toast.LENGTH_LONG).show()
            return
        }
        val needed = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            bleController.startScanAndConnect()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    // ------------------------------------------------------------------
    // BleControllerListener
    // ------------------------------------------------------------------
    override fun onStatusChanged(status: String, connected: Boolean) {
        runOnUiThread {
            binding.tvStatus.text = status
            isConnected = connected
            binding.btnConnect.text = if (connected) "Ngắt kết nối" else "Kết nối"
        }
    }

    override fun onStateReceived(json: JSONObject) {
        runOnUiThread {
            suppressSend = true
            try {
                if (json.has("power")) binding.switchPower.isChecked = json.optBoolean("power", true)
                if (json.has("mode")) binding.spinnerMode.setSelection(json.optInt("mode", 0))
                if (json.has("bright")) binding.seekBrightness.progress = json.optInt("bright", 128)
                if (json.has("speed")) binding.seekSpeed.progress = json.optInt("speed", 50)
                if (json.has("count")) binding.editLedCount.setText(json.optInt("count", 60).toString())
                if (json.has("pin")) binding.editGpioPin.setText(json.optInt("pin", 8).toString())
                if (json.has("color")) {
                    val c = json.optJSONArray("color")
                    if (c != null && c.length() >= 3) {
                        binding.seekR.progress = c.optInt(0)
                        binding.seekG.progress = c.optInt(1)
                        binding.seekB.progress = c.optInt(2)
                        updateColorPreview()
                    }
                }
                if (json.has("palette")) {
                    val pal = json.optJSONArray("palette")
                    if (pal != null && pal.length() > 0) {
                        paletteColors.clear()
                        for (i in 0 until pal.length()) {
                            val one = pal.optJSONArray(i) ?: continue
                            paletteColors.add(intArrayOf(one.optInt(0), one.optInt(1), one.optInt(2)))
                        }
                        paletteAdapter.notifyDataSetChanged()
                    }
                }
            } finally {
                suppressSend = false
            }
        }
    }
}
