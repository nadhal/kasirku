package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BluetoothPrinter(private val context: Context) {
    private val printMutex = Mutex()
    private val bluetoothManager: BluetoothManager? = try {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    } catch (e: Throwable) {
        null
    }
    private val bluetoothAdapter: BluetoothAdapter? = try {
        bluetoothManager?.adapter
    } catch (e: Throwable) {
        null
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // Standard SPP UUID for Bluetooth serial port profile
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    suspend fun getPairedDevices(): List<BluetoothDevice> = withContext(Dispatchers.IO) {
        try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("BluetoothPrinter", "Permission denied for bonded devices", e)
            emptyList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        printMutex.withLock {
            try {
                // Safely close previous connection
                try {
                    outputStream?.close()
                    bluetoothSocket?.close()
                } catch (e: Exception) {
                    Log.w("BluetoothPrinter", "Warning closing previous socket", e)
                }
                outputStream = null
                bluetoothSocket = null

                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                kotlinx.coroutines.withTimeout(15000) {
                    bluetoothSocket?.connect()
                }
                outputStream = bluetoothSocket?.outputStream
                true
            } catch (e: SecurityException) {
                Log.e("BluetoothPrinter", "Connect SecurityException: BLUETOOTH_CONNECT not granted", e)
                internalClose()
                false
            } catch (e: Exception) {
                Log.e("BluetoothPrinter", "Connection failed", e)
                internalClose()
                false
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        printMutex.withLock {
            internalClose()
        }
    }

    fun close() {
        internalClose()
    }

    private fun internalClose() {
        try {
            outputStream?.flush()
        } catch (e: Exception) {}
        try {
            outputStream?.close()
        } catch (e: Exception) {}
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {}
        outputStream = null
        bluetoothSocket = null
    }

    suspend fun printReceipt(
        cartInfo: List<CartItem>, 
        total: Double, 
        discount: Double = 0.0, 
        tax: Double = 0.0,
        paymentMethod: String = "Tunai",
        paymentAmount: Double = 0.0,
        changeAmount: Double = 0.0,
        storeName: String = "Toko Kasir",
        storeAddress: String = "Alamat Toko",
        storePhone: String = "081234567890",
        storeLogoUrl: String = "",
        printLogo: Boolean = true,
        receiptFooter: String = "Terima Kasih",
        receiptWidth: Int = 32,
        headerFontSize: String = "Besar",
        headerBold: Boolean = true,
        printAddress: Boolean = true,
        printPhone: Boolean = true,
        spacingAfterReceipt: Int = 3,
        logoSize: Int = 100
    ): Boolean = withContext(Dispatchers.IO) {
        printMutex.withLock {
            if (outputStream == null) return@withLock false

            try {
                // Use a standard decimal format with Rp prefix to match requested layout
                val decimalFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).apply { 
                    maximumFractionDigits = 0 
                }
                fun formatRp(amount: Double): String = "Rp ${decimalFormat.format(amount)}"
                
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val dateStr = dateFormat.format(Date())
                
                val subtotal = cartInfo.sumOf { it.product.price * it.quantity }
                val totalQuantity = cartInfo.sumOf { it.quantity }

                val builder = EscPosBuilder(receiptWidth).reset()

                // Draw Store Logo if configured
                if (printLogo && storeLogoUrl.isNotBlank()) {
                    try {
                        val logoBytes = getLogoPrintBytes(storeLogoUrl, receiptWidth, logoSize)
                        if (logoBytes != null) {
                            builder.alignCenter()
                            builder.writeRawBytes(logoBytes)
                            builder.feedLines(1)
                        } else {
                            Log.w("BluetoothPrinter", "Logo bytes are null for: $storeLogoUrl")
                        }
                    } catch (t: Throwable) {
                        Log.e("BluetoothPrinter", "Fatal error processing logo", t)
                    }
                }

                // Header (Store Name)
                builder.alignCenter()
                when (headerFontSize) {
                    "Sangat Besar" -> builder.setTextDoubleWidthAndHeight()
                    "Besar" -> builder.setTextDoubleHeight()
                    else -> builder.setTextNormal()
                }
                builder.setBold(headerBold)
                builder.printLine(storeName)

                // Reset formatting for sub-header
                builder.setTextNormal()
                builder.setBold(false)

                if (printAddress && storeAddress.isNotBlank()) {
                    builder.printLine(storeAddress)
                }
                if (printPhone && storePhone.isNotBlank()) {
                    builder.printLine("HP: $storePhone")
                }

                builder.printSeparator("=")
                builder.printLine("Waktu: $dateStr")
                builder.printSeparator("-")

                // Cart Items
                cartInfo.forEach { item ->
                    builder.alignLeft()
                    builder.printLine(item.product.name)
                    val qtyStr = "${item.quantity} x ${formatRp(item.product.price)}"
                    val itemTotal = formatRp(item.product.price * item.quantity)
                    builder.padSides("  $qtyStr", itemTotal)
                }

                builder.printSeparator("-")
                
                // Summary based on user-provided image structure
                builder.padSides("Nilai Bruto:", formatRp(subtotal))
                if (discount > 0.0) builder.padSides("Diskon:", "-${formatRp(discount)}")
                if (tax > 0.0) builder.padSides("Pajak:", formatRp(tax))
                
                builder.printSeparator("-")
                
                builder.setBold(true)
                builder.padSides("Nilai Total:", formatRp(total))
                builder.setBold(false)
                
                builder.padSides("Jumlah Kuantitas:", totalQuantity.toString())
                
                val paymentLabel = "Bayar ($paymentMethod):"
                builder.padSides(paymentLabel, formatRp(if (paymentAmount > 0) paymentAmount else total))
                
                builder.padSides("Kembalian:", formatRp(changeAmount))

                builder.printSeparator("=")

                // Footer
                builder.alignCenter()
                if (receiptFooter.isNotBlank()) {
                    builder.printLine(receiptFooter)
                }
                
                // Add a small spacer/separator before the final date if desired, 
                // but the image shows powered by... then *** Meta... ***
                // Let's just keep the footer text as is.
                
                // Feed extra spacing lines
                builder.feedLines(spacingAfterReceipt)
                
                // Write commands to printer stream with safe chunking and delays
                val rawBytes = builder.build()
                safeWriteToPrinter(rawBytes)

                true
            } catch (t: Throwable) {
                Log.e("BluetoothPrinter", "Critical printing failure", t)
                false
            }
        }
    }

    private suspend fun safeWriteToPrinter(bytes: ByteArray, chunkSize: Int = 256, delayMs: Long = 15) {
        withContext(Dispatchers.IO) {
            val stream = outputStream ?: return@withContext
            var offset = 0
            while (offset < bytes.size) {
                val len = minOf(chunkSize, bytes.size - offset)
                stream.write(bytes, offset, len)
                stream.flush()
                offset += len
                if (delayMs > 0 && offset < bytes.size) {
                    kotlinx.coroutines.delay(delayMs)
                }
            }
            // Allow the Bluetooth buffer to completely flush to the physical printer before closing socket
            try {
                kotlinx.coroutines.delay(1500)
            } catch (e: Exception) {}
        }
    }

    private var lastLogoUrl: String? = null
    private var lastLogoWidth: Int? = null
    private var cachedLogoBytes: ByteArray? = null

    private fun getLogoPrintBytes(filePath: String, receiptWidth: Int, logoSize: Int): ByteArray? {
        val baseWidth = if (receiptWidth <= 32) 256 else 384
        val maxAllowedDim = if (receiptWidth <= 32) 180 else 280
        val targetMaxWidth = (((baseWidth * logoSize / 100) / 8 * 8).coerceAtMost(maxAllowedDim)).coerceAtLeast(8)
        
        // Cache check
        if (filePath == lastLogoUrl && targetMaxWidth == lastLogoWidth && cachedLogoBytes != null) {
            return cachedLogoBytes
        }

        var original: android.graphics.Bitmap? = null
        var scaledBmp: android.graphics.Bitmap? = null
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) return null

            // 1. First get original image dimensions without full decode
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            // 2. Compute inSampleSize
            var inSampleSize = 1
            if (originalWidth > targetMaxWidth) {
                val halfWidth = originalWidth / 2
                while ((halfWidth / inSampleSize) >= targetMaxWidth) {
                    inSampleSize *= 2
                }
            }

            // 3. Decode sub-sampled bitmap
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            original = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            // 4. Precisely scale down to target maxWidth and cap height
            val scale = targetMaxWidth.toFloat() / original.width.toFloat()
            var targetHeight = (original.height * scale).toInt()
            
            // Cap height to maxAllowedDim to prevent OOM / giant prints
            if (targetHeight > maxAllowedDim) targetHeight = maxAllowedDim
            if (targetHeight <= 0) return null

            scaledBmp = android.graphics.Bitmap.createScaledBitmap(original, targetMaxWidth, targetHeight, true)
            
            val finalWidth = scaledBmp.width
            val finalHeight = scaledBmp.height
            
            val widthBytes = finalWidth / 8
            val byteCount = widthBytes * finalHeight
            val data = ByteArray(byteCount)
            
            // Optimization: Get all pixels at once
            val pixels = IntArray(finalWidth * finalHeight)
            scaledBmp.getPixels(pixels, 0, finalWidth, 0, 0, finalWidth, finalHeight)
            
            var index = 0
            for (y in 0 until finalHeight) {
                for (byteX in 0 until widthBytes) {
                    var tempByte = 0
                    for (bit in 0 until 8) {
                        val pixelX = byteX * 8 + bit
                        if (pixelX < finalWidth) {
                            val pixelColor = pixels[y * finalWidth + pixelX]
                            
                            // Integer math for grayscale conversion: (R*299 + G*587 + B*114) / 1000
                            val r = (pixelColor shr 16) and 0xff
                            val g = (pixelColor shr 8) and 0xff
                            val b = pixelColor and 0xff
                            val gray = (r * 299 + g * 587 + b * 114) / 1000
                            
                            val bitVal = if (gray < 180) 1 else 0
                            tempByte = (tempByte shl 1) or bitVal
                        } else {
                            tempByte = (tempByte shl 1)
                        }
                    }
                    data[index++] = tempByte.toByte()
                }
            }
            
            val xL = (widthBytes % 256).toByte()
            val xH = (widthBytes / 256).toByte()
            val yL = (finalHeight % 256).toByte()
            val yH = (finalHeight / 256).toByte()
            
            val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)
            val result = header + data
            
            // Update cache
            lastLogoUrl = filePath
            lastLogoWidth = targetMaxWidth
            cachedLogoBytes = result
            
            result
        } catch (t: Throwable) {
            Log.e("BluetoothPrinter", "Error generating logo bytes", t)
            null
        } finally {
            // Ensure bitmaps are recycled
            try {
                scaledBmp?.recycle()
                original?.recycle()
            } catch (e: Exception) {}
        }
    }

    private fun centerText(text: String, width: Int = 32): String {
        if (text.length >= width) return text
        val leftPad = (width - text.length) / 2
        return " ".repeat(leftPad) + text + " ".repeat(width - text.length - leftPad)
    }

    private fun padSides(left: String, right: String, width: Int = 32): String {
        val totalLength = left.length + right.length
        if (totalLength >= width) {
            return "$left $right"
        }
        val spaces = width - totalLength
        return left + " ".repeat(spaces) + right
    }
}
