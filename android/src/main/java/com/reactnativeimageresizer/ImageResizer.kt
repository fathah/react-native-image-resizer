package com.reactnativeimageresizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageResizer {
    private const val IMAGE_JPEG = "image/jpeg"
    private const val IMAGE_PNG = "image/png"
    private const val SCHEME_DATA = "data"
    private const val SCHEME_CONTENT = "content"
    private const val SCHEME_FILE = "file"
    private const val SCHEME_HTTP = "http"
    private const val SCHEME_HTTPS = "https"

    private val EXIF_TO_COPY_ROTATED = arrayOf(
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_MAX_APERTURE_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_BITS_PER_SAMPLE,
        ExifInterface.TAG_COMPRESSION,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_CONTRAST,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_EXIF_VERSION,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_INDEX,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FLASH_ENERGY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
        ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
        ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
        ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
        ExifInterface.TAG_PLANAR_CONFIGURATION,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_GAIN_CONTROL,
        ExifInterface.TAG_GAMMA,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_ISO_SPEED,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MAKER_NOTE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SATURATION,
        ExifInterface.TAG_SHARPNESS,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_SUBJECT_DISTANCE,
        ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
        ExifInterface.TAG_SUBJECT_LOCATION,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_WHITE_BALANCE
    )

    private fun resizeImage(
        image: Bitmap?,
        newWidth: Int,
        newHeight: Int,
        mode: String,
        onlyScaleDown: Boolean
    ): Bitmap? {
        if (image == null) return null

        val width = image.width
        val height = image.height

        if (newHeight > 0 && newWidth > 0) {
            var finalWidth: Int
            var finalHeight: Int

            if (mode == "stretch") {
                finalWidth = newWidth
                finalHeight = newHeight
                if (onlyScaleDown) {
                    finalWidth = min(width, finalWidth)
                    finalHeight = min(height, finalHeight)
                }
            } else {
                val widthRatio = newWidth.toFloat() / width
                val heightRatio = newHeight.toFloat() / height

                var ratio = if (mode == "cover") {
                    max(widthRatio, heightRatio)
                } else {
                    min(widthRatio, heightRatio)
                }

                if (onlyScaleDown) ratio = min(ratio, 1f)

                finalWidth = (width * ratio).roundToInt()
                finalHeight = (height * ratio).roundToInt()
            }

            return try {
                Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true)
            } catch (e: OutOfMemoryError) {
                null
            }
        }
        return null
    }

    private fun rotateImageFile(source: Bitmap, matrix: Matrix, angle: Float): Bitmap? {
        matrix.postRotate(angle)
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    @Throws(IOException::class)
    fun saveImageFile(
        bitmap: Bitmap?,
        saveDirectory: File,
        fileName: String,
        compressFormat: Bitmap.CompressFormat,
        quality: Int
    ): File {
        if (bitmap == null) {
            throw IOException("The bitmap couldn't be resized")
        }

        val newFile = File(saveDirectory, "$fileName.${compressFormat.name}")
        if (!newFile.createNewFile()) {
            throw IOException("The file already exists")
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(compressFormat, quality, outputStream)
        val bitmapData = outputStream.toByteArray()

        outputStream.flush()
        outputStream.close()

        val fos = FileOutputStream(newFile)
        fos.write(bitmapData)
        fos.flush()
        fos.close()

        return newFile
    }

    private fun getFileFromUri(context: Context, uri: Uri): File {
        var file = File(uri.path ?: "")
        if (file.exists()) return file

        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                if (cursor.moveToFirst()) {
                    val realPath = cursor.getString(columnIndex)
                    file = File(realPath)
                }
            }
        } catch (ignored: Exception) {
        }
        return file
    }

    fun copyExif(context: Context, imageUri: Uri, dstPath: String): Boolean {
        var src: ExifInterface? = null
        var dst: ExifInterface? = null

        try {
            val file = getFileFromUri(context, imageUri)
            if (!file.exists()) return false

            src = ExifInterface(file.absolutePath)
            dst = ExifInterface(dstPath)
        } catch (ignored: Exception) {
            Log.e("ImageResizer::copyExif", "EXIF read failed", ignored)
        }

        if (src == null || dst == null) return false

        try {
            for (attr in EXIF_TO_COPY_ROTATED) {
                val value = src.getAttribute(attr)
                if (value != null) {
                    dst.setAttribute(attr, value)
                }
            }
            dst.saveAttributes()
        } catch (ignored: Exception) {
            Log.e("ImageResizer::copyExif", "EXIF copy failed", ignored)
            return false
        }
        return true
    }

    private fun getOrientationMatrix(context: Context, uri: Uri): Matrix {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val ei = ExifInterface(input)
                    return getOrientationMatrix(ei)
                }
            }
            val file = getFileFromUri(context, uri)
            if (file.exists()) {
                val ei = ExifInterface(file.absolutePath)
                return getOrientationMatrix(ei)
            }
        } catch (ignored: Exception) {
        }
        return Matrix()
    }

    private fun getOrientationMatrix(exif: ExifInterface): Matrix {
        val matrix = Matrix()
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        return matrix
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @Throws(IOException::class)
    private fun loadBitmap(context: Context, imageUri: Uri, options: BitmapFactory.Options): Bitmap? {
        var sourceImage: Bitmap? = null
        val imageUriScheme = imageUri.scheme

        if (imageUriScheme == null || !imageUriScheme.equals(SCHEME_CONTENT, ignoreCase = true)) {
            try {
                sourceImage = BitmapFactory.decodeFile(imageUri.path, options)
            } catch (e: Exception) {
                e.printStackTrace()
                throw IOException("Error decoding image file")
            }
        } else {
            val cr = context.contentResolver
            cr.openInputStream(imageUri)?.use { input ->
                sourceImage = BitmapFactory.decodeStream(input, null, options)
            }
        }
        return sourceImage
    }

    @Throws(IOException::class)
    private fun loadBitmapFromFile(context: Context, imageUri: Uri, newWidth: Int, newHeight: Int): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        loadBitmap(context, imageUri, options)

        options.inSampleSize = calculateInSampleSize(options, newWidth, newHeight)
        options.inJustDecodeBounds = false
        return loadBitmap(context, imageUri, options)
    }

    @Throws(IOException::class)
    private fun loadBitmapFromURL(imageUri: Uri, newWidth: Int, newHeight: Int): Bitmap? {
        var sourceImage: Bitmap? = null
        var input: InputStream? = null

        try {
            val url = URL(imageUri.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()
            input = connection.inputStream

            if (input != null) {
                val buffer = ByteArrayOutputStream()
                var nRead: Int
                val data = ByteArray(1024)
                var imageData: ByteArray? = null

                try {
                    while (input.read(data, 0, data.size).also { nRead = it } != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    buffer.flush()
                    imageData = buffer.toByteArray()
                } finally {
                    buffer.close()
                }

                if (imageData != null) {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)

                    options.inSampleSize = calculateInSampleSize(options, newWidth, newHeight)
                    options.inJustDecodeBounds = false

                    sourceImage = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw IOException("Error fetching remote image file.")
        } finally {
            try {
                input?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return sourceImage
    }

    private fun loadBitmapFromBase64(imageUri: Uri): Bitmap? {
        var sourceImage: Bitmap? = null
        val imagePath = imageUri.schemeSpecificPart
        val commaLocation = imagePath.indexOf(',')
        if (commaLocation != -1) {
            val mimeType = imagePath.substring(0, commaLocation).replace('\\', '/').toLowerCase()
            val isJpeg = mimeType.startsWith(IMAGE_JPEG)
            val isPng = !isJpeg && mimeType.startsWith(IMAGE_PNG)

            if (isJpeg || isPng) {
                val encodedImage = imagePath.substring(commaLocation + 1)
                val decodedString = Base64.decode(encodedImage, Base64.DEFAULT)
                sourceImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            }
        }
        return sourceImage
    }

    @Throws(IOException::class)
    fun createResizedImage(
        context: Context,
        imageUri: Uri,
        newWidth: Int,
        newHeight: Int,
        quality: Int,
        rotation: Int,
        mode: String,
        onlyScaleDown: Boolean
    ): Bitmap? {
        var sourceImage: Bitmap? = null
        val imageUriScheme = imageUri.scheme

        if (imageUriScheme == null || 
            imageUriScheme.equals(SCHEME_FILE, ignoreCase = true) || 
            imageUriScheme.equals(SCHEME_CONTENT, ignoreCase = true)) {
            sourceImage = loadBitmapFromFile(context, imageUri, newWidth, newHeight)
        } else if (imageUriScheme.equals(SCHEME_HTTP, ignoreCase = true) || 
                   imageUriScheme.equals(SCHEME_HTTPS, ignoreCase = true)) {
            sourceImage = loadBitmapFromURL(imageUri, newWidth, newHeight)
        } else if (imageUriScheme.equals(SCHEME_DATA, ignoreCase = true)) {
            sourceImage = loadBitmapFromBase64(imageUri)
        }

        if (sourceImage == null) {
            throw IOException("Unable to load source image from path")
        }

        val matrix = getOrientationMatrix(context, imageUri)
        val rotatedImage = rotateImageFile(sourceImage, matrix, rotation.toFloat())

        if (rotatedImage == null) {
            throw IOException("Unable to rotate image. Most likely due to not enough memory.")
        }

        if (rotatedImage != sourceImage) {
            sourceImage.recycle()
        }

        val scaledImage = resizeImage(rotatedImage, newWidth, newHeight, mode, onlyScaleDown)

        if (scaledImage == null) {
            throw IOException("Unable to resize image. Most likely due to not enough memory.")
        }

        if (scaledImage != rotatedImage) {
            rotatedImage.recycle()
        }

        return scaledImage
    }
}
