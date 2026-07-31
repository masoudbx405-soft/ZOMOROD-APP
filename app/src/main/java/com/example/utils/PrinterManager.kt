package com.example.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean = false
)

/**
 * Real Bluetooth thermal-printer integration.
 *
 * Connects over the standard Serial Port Profile (SPP) and sends real
 * ESC/POS commands — no simulated delay(), no fake success. Farsi text is
 * rendered to a bitmap first (via StaticLayout with RTL text direction)
 * and sent as an ESC/POS raster image, since most low-cost thermal
 * printers cannot reliably render Persian glyphs from a text codepage —
 * rendering as an image works identically on every ESC/POS-compatible printer.
 */
object PrinterManager {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Standard raster width for common 58mm thermal printers at 203dpi.
    // (80mm printers typically use 576 — change here if targeting those instead.)
    private const val PRINTER_RASTER_WIDTH_PX = 384

    private val _connectedPrinter = MutableStateFlow<BluetoothPrinterDevice?>(null)
    val connectedPrinter: StateFlow<BluetoothPrinterDevice?> = _connectedPrinter

    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting

    private val _availablePrinters = MutableStateFlow<List<BluetoothPrinterDevice>>(emptyList())
    val availablePrinters: StateFlow<List<BluetoothPrinterDevice>> = _availablePrinters

    private var activeSocket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun scanPrinters(context: Context) {
        val list = mutableListOf<BluetoothPrinterDevice>()
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
                pairedDevices?.forEach { device ->
                    list.add(BluetoothPrinterDevice(device.name ?: "دستگاه ناشناس", device.address))
                }
            }
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT permission not granted yet — caller (MainDriverScreen)
            // is responsible for requesting it; we just return an empty list here.
        } catch (e: Exception) {
            // Bluetooth adapter unavailable on this device.
        }

        // Real bonded devices only — no fake demo entries. If the list is
        // empty, the user genuinely has no printer paired with this phone yet.
        _availablePrinters.value = list
    }

    /** Real SPP connection handshake — this actually opens a socket to the printer. */
    @SuppressLint("MissingPermission")
    suspend fun connectPrinter(device: BluetoothPrinterDevice): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    ?: return@withContext false
                val remoteDevice = bluetoothAdapter.getRemoteDevice(device.address)

                bluetoothAdapter.cancelDiscovery()
                val socket = remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                activeSocket?.close()
                activeSocket = socket
                _connectedPrinter.value = device.copy(isConnected = true)
                true
            } catch (e: IOException) {
                activeSocket = null
                _connectedPrinter.value = null
                false
            } catch (e: SecurityException) {
                false
            }
        }
    }

    fun disconnectPrinter() {
        try {
            activeSocket?.close()
        } catch (e: IOException) {
            // Already closed / unreachable — nothing more to do.
        }
        activeSocket = null
        _connectedPrinter.value = null
    }

    /** Real print job: renders the receipt to an image and streams real ESC/POS bytes to the printer. */
    suspend fun printReceipt(
        title: String,
        orderId: String,
        customerName: String,
        customerPhone: String,
        address: String,
        carpetDetails: String,
        totalPrice: Long,
        discount: Long,
        finalPrice: Long,
        paymentStatus: String,
        rackCode: String
    ): Boolean {
        val socket = activeSocket ?: return false

        return withContext(Dispatchers.IO) {
            _isPrinting.value = true
            try {
                val receiptText = buildEscPosThermalReceiptText(
                    title = title,
                    orderId = orderId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    address = address,
                    carpetItemsSummary = carpetDetails.split("\n").filter { it.isNotBlank() },
                    totalPrice = totalPrice,
                    discount = discount,
                    netPayable = finalPrice,
                    paymentMethod = paymentStatus,
                    rackCode = rackCode
                )

                val bitmap = renderReceiptToBitmap(receiptText)
                val escPosBytes = bitmapToEscPosRaster(bitmap)

                val out = socket.outputStream
                out.write(byteArrayOf(0x1B, 0x40)) // ESC @ : initialize printer
                out.write(escPosBytes)
                out.write(byteArrayOf(0x0A, 0x0A, 0x0A)) // feed a few lines before cut
                out.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 : full paper cut
                out.flush()

                true
            } catch (e: IOException) {
                disconnectPrinter()
                false
            } finally {
                _isPrinting.value = false
            }
        }
    }

    /** Renders Farsi receipt text correctly (RTL shaping) into a monochrome-ready bitmap. */
    private fun renderReceiptToBitmap(text: String): Bitmap {
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, PRINTER_RASTER_WIDTH_PX)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(4f, 1.1f)
            .build()

        val bitmap = Bitmap.createBitmap(PRINTER_RASTER_WIDTH_PX, staticLayout.height + 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(0f, 10f)
        staticLayout.draw(canvas)
        return bitmap
    }

    /** Converts a bitmap into ESC/POS raster-image bytes (GS v 0), the universal way to print bitmaps on thermal printers. */
    private fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        val header = byteArrayOf(
            0x1D, 0x76, 0x30, 0x00, // GS v 0 : print raster bit image, normal size
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (bitmap.height and 0xFF).toByte(),
            ((bitmap.height shr 8) and 0xFF).toByte()
        )

        val imageData = ByteArray(widthBytes * bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                val isBlack = luminance < 160
                if (isBlack) {
                    val byteIndex = y * widthBytes + (x / 8)
                    val bitIndex = 7 - (x % 8)
                    imageData[byteIndex] = (imageData[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }

        return header + imageData
    }

    fun buildEscPosThermalReceiptText(
        title: String,
        orderId: String,
        customerName: String,
        customerPhone: String,
        address: String,
        carpetItemsSummary: List<String>,
        totalPrice: Long,
        discount: Long,
        netPayable: Long,
        paymentMethod: String,
        rackCode: String,
        includeTwoCopies: Boolean = true
    ): String {
        fun buildSingleCopy(copyTitle: String): String {
            val sb = StringBuilder()
            sb.append("===============================\n")
            sb.append("     *** قالیشویی زمرد ***\n")
            sb.append("    $title\n")
            sb.append("     >>>> $copyTitle <<<<\n")
            sb.append("===============================\n")
            sb.append("شماره فاکتور: $orderId\n")
            sb.append("تاریخ و زمان: ${FarsiUtils.formatCurrentTimeFarsi()}\n")
            sb.append("نام مشتری: $customerName\n")
            sb.append("تلفن تماس: $customerPhone\n")
            sb.append("آدرس: $address\n")
            sb.append("-------------------------------\n")
            sb.append("اقلام سفارش (فرش‌ها):\n")
            carpetItemsSummary.forEachIndexed { index, item ->
                sb.append("${index + 1}. $item\n")
            }
            sb.append("-------------------------------\n")
            if (rackCode.isNotEmpty()) {
                sb.append("شماره قفسه انبار: $rackCode\n")
                sb.append("-------------------------------\n")
            }
            sb.append("مبلغ کل فرش‌ها: ${FarsiUtils.formatPrice(totalPrice)}\n")
            if (discount > 0) {
                sb.append("مبلغ تخفیف: ${FarsiUtils.formatPrice(discount)}\n")
            }
            sb.append("مبلغ قابل پرداخت: ${FarsiUtils.formatPrice(netPayable)}\n")
            sb.append("وضعیت تسویه: $paymentMethod\n")
            sb.append("-------------------------------\n")
            sb.append("  [ بارکد / QR کد پیگیری: ORD-$orderId ]\n")
            sb.append("===============================\n")
            sb.append(" امضاء و تایید تحویل‌گیرنده ($copyTitle):\n\n\n")
            sb.append("...............................\n")
            sb.append("سامانه انحصاری قالیشویی زمرد\n")
            sb.append("===============================\n")
            return sb.toString()
        }

        return if (includeTwoCopies) {
            buildSingleCopy("نسخه مشتری") +
                    "\n\n- - - - - - - - - - - - - - - -\n" +
                    "      محل برش کاغذ پرینتر      \n" +
                    "- - - - - - - - - - - - - - - -\n\n" +
                    buildSingleCopy("نسخه راننده")
        } else {
            buildSingleCopy("نسخه تک برگ")
        }
    }
}
