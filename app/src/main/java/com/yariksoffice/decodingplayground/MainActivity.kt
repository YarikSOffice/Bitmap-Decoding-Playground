package com.yariksoffice.decodingplayground

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import java.lang.IllegalStateException
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ImageView
    private lateinit var text: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpViews()
    }

    private fun setUpViews() {
        preview = findViewById(R.id.preview)
        text = findViewById(R.id.size)
        scroll = findViewById(R.id.scroll)
        findViewById<Button>(R.id.button1).setOnClickListener {
            chooseImage(BITMAP_FACTORY_SCALE)
        }
        findViewById<Button>(R.id.button2).setOnClickListener {
            chooseImage(REGION_DECODER_SCALE_AND_CROP)
        }
        findViewById<Button>(R.id.button3).setOnClickListener {
            requestIfPie(IMAGE_DECODER_SCALE)
        }
        findViewById<Button>(R.id.button4).setOnClickListener {
            requestIfPie(IMAGE_DECODER_SCALE_AND_CROP)
        }
    }

    private fun requestIfPie(requestCode: Int) {
        if (isAtLeastPie()) {
            chooseImage(requestCode)
        } else {
            Toast.makeText(this, "Android P is required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseImage(requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType(INTENT_IMAGE_TYPE)
        startActivityForResult(intent, requestCode)
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val image = data.data!!
            val initialSize = getImageSize(image)

            // use main thread here for the sake of simplicity
            val bitmap = when (requestCode) {
                BITMAP_FACTORY_SCALE -> decodeScaledBitmap(image) // ignore exif orientation tags
                REGION_DECODER_SCALE_AND_CROP -> decodeScaledAndCroppedBitmap(image) // ignore exif
                IMAGE_DECODER_SCALE -> decodeScaledBitmapWithTargetSize(image) // respect exif
                IMAGE_DECODER_SCALE_AND_CROP -> decodeScaledBitmapWithTargetSampleSize(image)// respect exif
                else -> throw IllegalStateException()
            }

            preview.setImageBitmap(bitmap)

            val finalSize = Size(bitmap.width, bitmap.height)
            text.text = "Initial: $initialSize\nFinal: $finalSize"

            scroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun decodeScaledBitmap(image: Uri): Bitmap {
        val initialSize = getImageSize(image)

        val requiredWidth = min(REQUIRED_IMAGE_WIDTH, initialSize.width)
        val sourceWidth = initialSize.width
        val sampleSize = calculateSampleSize(sourceWidth, requiredWidth)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inDensity = sourceWidth
            inTargetDensity = requiredWidth * sampleSize
        }
        val input = contentResolver.openInputStream(image)!!
        val bitmap = BitmapFactory.decodeStream(input, null, options)!!
        // reset density to display bitmap correctly
        bitmap.density = resources.displayMetrics.densityDpi
        return bitmap
    }

    private fun decodeScaledAndCroppedBitmap(image: Uri): Bitmap {
        val initialSize = getImageSize(image)
        val requiredWidth = min(REQUIRED_IMAGE_WIDTH, initialSize.width)

        val cropRect = Rect(0, 0, initialSize.width, initialSize.height / 2)

        val input = contentResolver.openInputStream(image)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(initialSize.width, requiredWidth)
        }
        return BitmapRegionDecoder.newInstance(input, true).decodeRegion(cropRect, options)
    }

    @TargetApi(VERSION_CODES.P)
    private fun decodeScaledBitmapWithTargetSize(image: Uri): Bitmap {
        val header = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
            val size = info.size
            val requiredWidth = minOf(REQUIRED_IMAGE_WIDTH, size.width)
            val coefficient = requiredWidth / size.width.toDouble()
            val newHeight = (size.height * coefficient).toInt()
            decoder.setTargetSize(requiredWidth, newHeight)
        }
        val source = ImageDecoder.createSource(contentResolver, image)
        return ImageDecoder.decodeBitmap(source, header)
    }

    @TargetApi(VERSION_CODES.P)
    private fun decodeScaledBitmapWithTargetSampleSize(image: Uri): Bitmap {
        val header = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
            val size = info.size
            val sampleSize = calculateSampleSize(size.width, min(REQUIRED_IMAGE_WIDTH, size.width))
            val newSize = Size(size.width / sampleSize, size.height / sampleSize)
            decoder.setTargetSampleSize(sampleSize)
            decoder.crop = Rect(0, 0, newSize.width, newSize.height / 2)
        }
        val source = ImageDecoder.createSource(contentResolver, image)
        return ImageDecoder.decodeBitmap(source, header)
    }

    private fun getImageSize(image: Uri): Size {
        val input = contentResolver.openInputStream(image)!!
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(input, null, options)
        return Size(options.outWidth, options.outHeight)
    }

    private fun calculateSampleSize(currentWidth: Int, requiredWidth: Int): Int {
        var inSampleSize = 1
        if (currentWidth > requiredWidth) {
            val halfWidth = currentWidth / 2
            // Calculate the largest inSampleSize value that is a power of 2 and keeps
            // width larger than the requested width
            while (halfWidth / inSampleSize >= requiredWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        private const val INTENT_IMAGE_TYPE = "image/*"
        private const val BITMAP_FACTORY_SCALE = 770
        private const val REGION_DECODER_SCALE_AND_CROP = 771
        private const val IMAGE_DECODER_SCALE = 772
        private const val IMAGE_DECODER_SCALE_AND_CROP = 773
        private const val REQUIRED_IMAGE_WIDTH = 500
    }

    data class Size(val width: Int, val height: Int)

    private fun isAtLeastPie(): Boolean {
        return VERSION.SDK_INT >= VERSION_CODES.P
    }
}
