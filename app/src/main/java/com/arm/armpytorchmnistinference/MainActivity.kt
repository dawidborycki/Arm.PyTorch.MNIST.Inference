package com.arm.armpytorchmnistinference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.random.Random
import kotlin.system.measureNanoTime

class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var trueLabel: TextView
    private lateinit var selectImageButton: Button
    private lateinit var runInferenceButton: Button
    private lateinit var predictedLabel: TextView
    private lateinit var inferenceTime: TextView
    private lateinit var model: Module
    private var currentBitmap: Bitmap? = null
    private var currentTrueLabel: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        imageView = findViewById(R.id.imageView)
        trueLabel = findViewById(R.id.trueLabel)
        selectImageButton = findViewById(R.id.selectImageButton)
        runInferenceButton = findViewById(R.id.runInferenceButton)
        predictedLabel = findViewById(R.id.predictedLabel)
        inferenceTime = findViewById(R.id.inferenceTime)

        // Load model from assets
        model = Module.load(assetFilePath("model.pth"))

        // Set up button click listener for selecting random image
        selectImageButton.setOnClickListener {
            selectRandomImageFromAssets()
        }

        // Set up button click listener for running inference
        runInferenceButton.setOnClickListener {
            currentBitmap?.let { bitmap ->
                runInference(bitmap)
            }
        }
    }

    private fun selectRandomImageFromAssets() {
        try {
            // Get list of files in the mnist_bitmaps folder
            val assetManager = assets
            val files = assetManager.list("mnist_bitmaps") ?: arrayOf()

            if (files.isEmpty()) {
                trueLabel.text = "No images found in assets/mnist_bitmaps"
                return
            }

            // Select a random file from the list
            val randomFile = files[Random.nextInt(files.size)]
            val inputStream: InputStream = assetManager.open("mnist_bitmaps/$randomFile")
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Extract the true label from the filename (e.g., 07_00.png -> true label is 7)
            currentTrueLabel = randomFile.split("_")[0].toInt()

            // Display the image and its true label
            imageView.setImageBitmap(bitmap)
            trueLabel.text = "True Label: $currentTrueLabel"

            // Set the current bitmap for inference
            currentBitmap = bitmap
        } catch (e: IOException) {
            e.printStackTrace()
            trueLabel.text = "Error loading image from assets"
        }
    }

    // Method to convert a grayscale bitmap to a float array and create a tensor with shape [1, 1, 28, 28]
    private fun createTensorFromBitmap(bitmap: Bitmap): Tensor {
        // Ensure the bitmap is in the correct format (grayscale) and dimensions [28, 28]
        if (bitmap.width != 28 || bitmap.height != 28) {
            throw IllegalArgumentException("Expected bitmap of size [28, 28], but got [${bitmap.width}, ${bitmap.height}]")
        }

        // Convert the grayscale bitmap to a float array
        val width = bitmap.width
        val height = bitmap.height
        val floatArray = FloatArray(width * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            // Normalize pixel values to [0, 1] range, assuming the grayscale image stores values in the R channel
            floatArray[i] = (pixels[i] and 0xFF) / 255.0f
        }

        // Create a tensor with shape [1, 1, 28, 28] (batch size, channels, height, width)
        return Tensor.fromBlob(floatArray, longArrayOf(1, 1, height.toLong(), width.toLong()))
    }

    private fun runInference(bitmap: Bitmap) {
        // Convert bitmap to a float array and create a tensor with shape [1, 1, 28, 28]
        val inputTensor = createTensorFromBitmap(bitmap)

        // Run inference and measure time
        val inferenceTimeMicros = measureTimeMicros {
            // Forward pass through the model
            val outputTensor = model.forward(IValue.from(inputTensor)).toTensor()
            val scores = outputTensor.dataAsFloatArray

            // Get the index of the class with the highest score
            val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: -1
            predictedLabel.text = "Predicted Label: $maxIndex"
        }

        // Update inference time TextView in microseconds
        inferenceTime.text = "Inference Time: $inferenceTimeMicros µs"
    }

    // Method to measure execution time in microseconds
    private inline fun measureTimeMicros(block: () -> Unit): Long {
        val time = measureNanoTime(block)
        return time / 1000 // Convert nanoseconds to microseconds
    }

    // Helper function to get the file path from assets
    private fun assetFilePath(assetName: String): String {
        val file = File(filesDir, assetName)
        assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file.absolutePath
    }
}