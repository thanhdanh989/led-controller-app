package com.danh.ledcontroller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.GridLayout
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

    // 16 mau co san de chon nhanh (thay vi phai keo tung thanh truot R/G/B).
    // Trung khop voi cac mau preset_01..16 trong colors.xml.
    private val presetColors: List<IntArray> = listOf(
        intArrayOf(255, 0, 0),     // Do
        intArrayOf(255, 69, 0),    // Do cam
        intArrayOf(255, 140, 0),   // Cam
        intArrayOf(255, 215, 0),   // Vang cam (gold)
        intArrayOf(255, 255, 0),   // Vang
        intArrayOf(173, 255, 47),  // Vang xanh
        intArrayOf(0, 255, 0),     // Xanh la
        intArrayOf(0, 250, 154),   // Xanh la ngoc
        intArrayOf(0, 255, 255),   // Ngoc (cyan)
        intArrayOf(30, 144, 255),  // Xanh duong nhat
        intArrayOf(0, 0, 255),     // Xanh duong
        intArrayOf(75, 0, 130),    // Cham (indigo)
        intArrayOf(138, 43, 226),  // Tim
        intArrayOf(255, 0, 255),   // Hong canh sen
        intArrayOf(255, 20, 147),  // Hong dam
        intArrayOf(255, 255, 255) // Trang
    )

    private var isConnected = false
    // Bat len khi dang tu dong cap nhat UI tu trang thai thiet bi, de khong gui nguoc lenh
    private var suppressSend = false
    // Mode ma NGUOI DUNG vua chon va dang cho ESP32 xac nhan xong (transition ~700ms).
    // Trong luc cho, moi JSON trang thai bao "mode" cu (chua kip cap nhat) se bi BO QUA
    // hoan toan (khong dong bo Spinner), de tranh vong lap: app tuong nguoi dung chon lai
    // mode cu roi tu gui lenh doi ve mode cu, huy mat lua chon vua roi.
    private var expectedMode: Int? = null

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
        setupColorPresets()
        setupButtons()
    }

    // ------------------------------------------------------------------
    private fun setupModeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, LED_MODES)
        binding.spinnerMode.adapter = adapter
        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (suppressSend) return
                expectedMode = position
                bleController.sendCommand(BleProtocol.cmdMode(position))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ------------------------------------------------------------------
    // Luoi 16 mau co san (man hinh chinh) - bam la gui mau ngay, khong can chinh tung thanh truot.
    // Van giu nut "Tuy chinh mau khac..." de mo lai 3 thanh truot R/G/B neu muon can chinh sau.
    private fun setupColorPresets() {
        buildPresetGrid(binding.gridColorPresets) { rgb ->
            binding.seekR.progress = rgb[0]
            binding.seekG.progress = rgb[1]
            binding.seekB.progress = rgb[2]
            updateColorPreview()
            if (!suppressSend) sendSolidColorNow()
        }
        binding.btnToggleCustomColor.setOnClickListener {
            val showing = binding.layoutCustomColor.visibility == View.VISIBLE
            binding.layoutCustomColor.visibility = if (showing) View.GONE else View.VISIBLE
            binding.btnToggleCustomColor.text = if (showing) "Tùy chỉnh màu khác... ▾" else "Tùy chỉnh màu khác... ▴"
        }
    }

    /** Tao 16 o mau (View vuong, co vien mong) trong 1 GridLayout 4 cot, goi onPick(rgb) khi bam. */
    private fun buildPresetGrid(grid: GridLayout, onPick: (IntArray) -> Unit) {
        grid.removeAllViews()
        val density = resources.displayMetrics.density
        val sizePx = (44 * density).toInt()
        val marginPx = (4 * density).toInt()
        presetColors.forEach { rgb ->
            val swatch = View(this)
            val params = GridLayout.LayoutParams().apply {
                width = sizePx
                height = sizePx
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            swatch.layoutParams = params
            val drawable = GradientDrawable().apply {
                setColor(Color.rgb(rgb[0], rgb[1], rgb[2]))
                setStroke((1 * density).toInt(), Color.parseColor("#DDDDDD"))
                cornerRadius = 6f * density
            }
            swatch.background = drawable
            swatch.setOnClickListener { onPick(rgb) }
            grid.addView(swatch)
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

        // Luoi 16 mau co san: bam la chon ngay (chi cap nhat preview + 3 thanh truot ben trong
        // dialog), van phai bam "Thêm" moi thuc su xac nhan - giu nguyen hanh vi cu.
        buildPresetGrid(dialogBinding.dialogGridPresets) { rgb ->
            dialogBinding.dialogSeekR.progress = rgb[0]
            dialogBinding.dialogSeekG.progress = rgb[1]
            dialogBinding.dialogSeekB.progress = rgb[2]
            updatePreview()
        }
        dialogBinding.dialogToggleCustom.setOnClickListener {
            val showing = dialogBinding.dialogCustomColorLayout.visibility == View.VISIBLE
            dialogBinding.dialogCustomColorLayout.visibility = if (showing) View.GONE else View.VISIBLE
            dialogBinding.dialogToggleCustom.text = if (showing) "Tùy chỉnh màu khác... ▾" else "Tùy chỉnh màu khác... ▴"
        }

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
            if (!connected) expectedMode = null // mat ket noi -> huy moi cho xac nhan mode dang do dang
        }
    }

    override fun onStateReceived(json: JSONObject) {
        runOnUiThread {
            suppressSend = true
            try {
                if (json.has("power")) binding.switchPower.isChecked = json.optBoolean("power", true)
                if (json.has("mode")) {
                    val deviceMode = json.optInt("mode", 0)
                    val waitingFor = expectedMode
                    if (waitingFor != null) {
                        // Dang cho ESP32 hoan tat chuyen sang mode nguoi dung vua chon (~700ms
                        // fade qua den roi fade vao). Trong luc do, ESP32 van con bao mode CU vi
                        // no chua kip cap nhat state.mode that su. Neu goi setSelection() voi gia
                        // tri cu nay, Spinner se coi nhu "nguoi dung chon lai mode cu" (callback
                        // no chay tre 1 nhip) va tu gui nguoc lenh doi ve mode cu -> huy mat lua
                        // chon vua roi. Vi vay: BO QUA hoan toan, khong dong bo Spinner, cho toi
                        // khi ESP32 bao dung mode da cho.
                        if (deviceMode == waitingFor) expectedMode = null
                        // deviceMode != waitingFor -> van dang cho, khong lam gi them ca.
                    } else {
                        binding.spinnerMode.setSelection(deviceMode)
                    }
                }
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
                // QUAN TRONG: Spinner.setSelection() goi onItemSelected qua post() (bat dong bo,
                // chay tre 1 vong Looper), khong goi ngay lap tuc. Neu tat suppressSend ngay tai day
                // (dong bo), callback tre cua lan setSelection() ben tren se chay SAU khi cờ da tat,
                // khien app tuong nguoi dung vua chon lai che do CU va tu gui nguoc lenh doi mode cu
                // -> che do moi nguoi dung vua chon bi huy gan nhu ngay lap tuc.
                // Doi 1 vong Looper nua (post) roi moi tat co, de callback tre do bi chan dung cach.
                binding.root.post { suppressSend = false }
            }
        }
    }
}
