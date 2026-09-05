package com.danh.ledcontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

private const val TAG = "BleController"

interface BleControllerListener {
    fun onStatusChanged(status: String, connected: Boolean)
    fun onStateReceived(json: JSONObject)
}

/**
 * Bao boc toan bo logic BLE: quet -> ket noi -> discover service -> gui/nhan lenh.
 * Goi cac ham public tu MainActivity SAU KHI da xin đủ quyen Bluetooth runtime.
 */
class BleController(private val context: Context, private val listener: BleControllerListener) {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Hang doi lenh: tranh gui chong lenh khi lenh truoc chua xong (BLE write tuan tu se on dinh hon)
    private val commandQueue = ArrayDeque<String>()
    private var writeInFlight = false

    private var scanning = false

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            listener.onStatusChanged("Bluetooth đang tắt trên máy", false)
            return
        }
        val scanner = a.bluetoothLeScanner
        if (scanner == null) {
            listener.onStatusChanged("Không tìm thấy BLE scanner", false)
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(BleProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        listener.onStatusChanged("Đang quét thiết bị...", false)
        scanning = true
        scanner.startScan(listOf(filter), settings, scanCallback)

        // Tu dong dung quet sau 10s neu khong thay
        mainHandler.postDelayed({
            if (scanning) {
                scanner.stopScan(scanCallback)
                scanning = false
                if (gatt == null) listener.onStatusChanged("Không tìm thấy ESP32-LED. Kiểm tra board đã bật chưa.", false)
            }
        }, 10_000)
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device
            adapter?.bluetoothLeScanner?.stopScan(this)
            scanning = false
            listener.onStatusChanged("Tìm thấy, đang kết nối...", false)
            gatt = device.connectGatt(context, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onStatusChanged("Quét thất bại (mã lỗi $errorCode)", false)
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onStatusChanged("Đã kết nối, đang dò dịch vụ...", false)
                g.requestMtu(247) // xin MTU lon de nhan du JSON trang thai
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener.onStatusChanged("Mất kết nối", false)
                rxChar = null
                gatt = null
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(BleProtocol.SERVICE_UUID)
            if (service == null) {
                listener.onStatusChanged("Không tìm thấy dịch vụ trên thiết bị", false)
                return
            }
            rxChar = service.getCharacteristic(BleProtocol.CHAR_RX_UUID)
            val txChar = service.getCharacteristic(BleProtocol.CHAR_TX_UUID)
            listener.onStatusChanged("Đã kết nối ESP32-LED", true)

            if (txChar != null) {
                g.setCharacteristicNotification(txChar, true)
                val cccd = txChar.getDescriptor(BleProtocol.CCCD_UUID)
                if (cccd != null) {
                    // QUAN TRONG: khong duoc goi writeCharacteristic() truoc khi thao tac nay
                    // hoan tat (BLE chi cho phep 1 thao tac GATT chay tai 1 thoi diem).
                    // Lenh "get" dau tien se duoc gui trong onDescriptorWrite() ben duoi.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptorCompatValue.ENABLE_NOTIFICATION)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptorCompatValue.ENABLE_NOTIFICATION
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                } else {
                    sendCommand(BleProtocol.cmdGet())
                }
            } else {
                sendCommand(BleProtocol.cmdGet())
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: android.bluetooth.BluetoothGattDescriptor, status: Int) {
            // Bat notify xong -> gio moi an toan de gui lenh dau tien
            sendCommand(BleProtocol.cmdGet())
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Duong danh cho API < 33
            handleIncoming(characteristic.value ?: return)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeInFlight = false
            processQueue()
        }
    }

    private fun handleIncoming(bytes: ByteArray) {
        val text = String(bytes, StandardCharsets.UTF_8)
        try {
            val json = JSONObject(text)
            mainHandler.post { listener.onStateReceived(json) }
        } catch (e: Exception) {
            Log.w(TAG, "Không đọc được JSON trạng thái: $text")
        }
    }

    /** Gui 1 lenh JSON (dang String) xuong ESP32. An toan de goi lien tuc, se tu xep hang. */
    @SuppressLint("MissingPermission")
    fun sendCommand(json: String) {
        commandQueue.addLast(json)
        processQueue()
    }

    @SuppressLint("MissingPermission")
    private fun processQueue() {
        if (writeInFlight) return
        val g = gatt ?: return
        val ch = rxChar ?: return
        val next = commandQueue.pollFirst() ?: return
        writeInFlight = true
        val bytes = next.toByteArray(StandardCharsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        rxChar = null
        listener.onStatusChanged("Đã ngắt kết nối", false)
    }
}

/** Gia tri chuan cho descriptor CCCD (bat notify), tranh phai import them hang so cua Android framework moi. */
object BluetoothGattDescriptorCompatValue {
    val ENABLE_NOTIFICATION: ByteArray = byteArrayOf(0x01, 0x00)
}
