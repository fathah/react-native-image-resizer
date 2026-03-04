package com.reactnativeimageresizer

import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.GuardedAsyncTask
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import java.io.File
import java.io.IOException
import java.util.UUID

class ImageResizerModule(reactContext: ReactApplicationContext) : ImageResizerSpec(reactContext) {

    override fun getName(): String {
        return NAME
    }

    @ReactMethod
    override fun createResizedImage(
        uri: String,
        width: Double,
        height: Double,
        format: String,
        quality: Double,
        mode: String,
        onlyScaleDown: Boolean,
        rotation: Double,
        outputPath: String?,
        keepMeta: Boolean,
        promise: Promise
    ) {
        val options = Arguments.createMap()
        options.putString("mode", mode)
        options.putBoolean("onlyScaleDown", onlyScaleDown)

        // Run in guarded async task to prevent blocking the React bridge
        object : GuardedAsyncTask<Void, Void>(reactApplicationContext) {
            override fun doInBackgroundGuarded(vararg params: Void?) {
                try {
                    val response = createResizedImageWithExceptions(
                        imagePath = uri,
                        newWidth = width.toInt(),
                        newHeight = height.toInt(),
                        compressFormatString = format,
                        quality = quality.toInt(),
                        rotation = rotation.toInt(),
                        outputPath = outputPath,
                        keepMeta = keepMeta,
                        options = options
                    )
                    promise.resolve(response)
                } catch (e: IOException) {
                    promise.reject(e)
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(IOException::class)
    private fun createResizedImageWithExceptions(
        imagePath: String,
        newWidth: Int,
        newHeight: Int,
        compressFormatString: String,
        quality: Int,
        rotation: Int,
        outputPath: String?,
        keepMeta: Boolean,
        options: ReadableMap
    ): Any {
        val compressFormat = Bitmap.CompressFormat.valueOf(compressFormatString)
        val imageUri = Uri.parse(imagePath)

        val scaledImage = ImageResizer.createResizedImage(
            context = reactApplicationContext,
            imageUri = imageUri,
            newWidth = newWidth,
            newHeight = newHeight,
            quality = quality,
            rotation = rotation,
            mode = options.getString("mode") ?: "contain",
            onlyScaleDown = options.getBoolean("onlyScaleDown")
        ) ?: throw IOException("The image failed to be resized; invalid Bitmap result.")

        // Save the resulting image
        var path = reactApplicationContext.cacheDir
        if (outputPath != null) {
            path = File(outputPath)
        }

        val resizedImage = ImageResizer.saveImageFile(
            bitmap = scaledImage,
            saveDirectory = path,
            fileName = UUID.randomUUID().toString(),
            compressFormat = compressFormat,
            quality = quality
        )
        val response = Arguments.createMap()

        // If resizedImagePath is empty and this wasn't caught earlier, throw.
        if (resizedImage.isFile) {
            response.putString("path", resizedImage.absolutePath)
            response.putString("uri", Uri.fromFile(resizedImage).toString())
            response.putString("name", resizedImage.name)
            response.putDouble("size", resizedImage.length().toDouble())
            response.putDouble("width", scaledImage.width.toDouble())
            response.putDouble("height", scaledImage.height.toDouble())

            // Copy file's metadata/exif info if required
            if (keepMeta) {
                try {
                    ImageResizer.copyExif(reactApplicationContext, imageUri, resizedImage.absolutePath)
                } catch (ignored: Exception) {
                    Log.e("ImageResizer::createResizedImageWithExceptions", "EXIF copy failed", ignored)
                }
            }
        } else {
            throw IOException("Error getting resized image path")
        }

        // Clean up bitmap
        scaledImage.recycle()
        return response
    }

    companion object {
        const val NAME = "ImageResizer"
    }
}
