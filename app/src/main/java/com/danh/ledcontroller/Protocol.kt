package com.danh.ledcontroller

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Giao thuc BLE dung chung voi firmware ESP32 (xem HUONG_DAN.md o firmware).
 * Dung chuan Nordic UART Service (NUS) de tuong thich voi nhieu app BLE terminal khac.
 */
object BleProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val CHAR_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // dien thoai -> ESP32
    val CHAR_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // ESP32 -> dien thoai
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME = "ESP32-LED"

    fun cmdPower(on: Boolean): String = JSONObject().apply {
        put("cmd", "power"); put("on", if (on) 1 else 0)
    }.toString()

    fun cmdMode(id: Int): String = JSONObject().apply {
        put("cmd", "mode"); put("id", id)
    }.toString()

    fun cmdColor(r: Int, g: Int, b: Int): String = JSONObject().apply {
        put("cmd", "color"); put("r", r); put("g", g); put("b", b)
    }.toString()

    fun cmdBrightness(v: Int): String = JSONObject().apply {
        put("cmd", "bright"); put("v", v)
    }.toString()

    fun cmdSpeed(v: Int): String = JSONObject().apply {
        put("cmd", "speed"); put("v", v)
    }.toString()

    fun cmdCount(v: Int): String = JSONObject().apply {
        put("cmd", "count"); put("v", v)
    }.toString()

    fun cmdPin(v: Int): String = JSONObject().apply {
        put("cmd", "pin"); put("v", v)
    }.toString()

    fun cmdPalette(colors: List<IntArray>): String = JSONObject().apply {
        put("cmd", "palette")
        val arr = JSONArray()
        for (c in colors) {
            val one = JSONArray()
            one.put(c[0]); one.put(c[1]); one.put(c[2])
            arr.put(one)
        }
        put("colors", arr)
    }.toString()

    fun cmdGet(): String = JSONObject().apply { put("cmd", "get") }.toString()
}

/** Danh sach 20 che do, thu tu id phai KHOP CHINH XAC voi Config.h ben firmware. */
val LED_MODES = listOf(
    "0 - Màu đơn",
    "1 - Xếp gạch theo màu",
    "2 - Quét màu",
    "3 - Cầu vồng chạy",
    "4 - Đuổi cầu vồng",
    "5 - Đèn sân khấu",
    "6 - Hơi thở",
    "7 - Lửa cháy",
    "8 - Sao chổi",
    "9 - Quét radar",
    "10 - Lấp lánh",
    "11 - Confetti",
    "12 - Sóng chạy",
    "13 - Đuổi điểm sáng",
    "14 - Quét gradient",
    "15 - Nhấp nháy",
    "16 - Đổi màu ngẫu nhiên",
    "17 - Plasma",
    "18 - Nảy qua lại",
    "19 - Chuyển màu theo bảng"
)
