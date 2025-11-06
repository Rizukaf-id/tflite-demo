/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.imageclassification

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.IOException

class ImageClassifierHelper(
    @Volatile var threshold: Float = 0.5f,
    @Volatile var numThreads: Int = 2,
    @Volatile var maxResults: Int = 3,
    @Volatile var currentDelegate: Int = 0,
    @Volatile var currentModel: Int = 0,
    val context: Context,
    val imageClassifierListener: ClassifierListener?
) {

    // For the Task API
    private var imageClassifier: ImageClassifier? = null
    // For the Interpreter API
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var inputTensorWidth: Int = 0
    private var inputTensorHeight: Int = 0

    init {
        setupImageClassifier()
    }

    fun clearImageClassifier() {
        imageClassifier?.close()
        imageClassifier = null
        interpreter?.close()
        interpreter = null
    }

    fun setupImageClassifier() {
        clearImageClassifier()
        if (currentModel == MODEL_UNQUANT) {
            setupInterpreter()
        } else {
            setupTaskApiClassifier()
        }
    }

    private fun setupTaskApiClassifier() {
        val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)

        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

        when (currentDelegate) {
            DELEGATE_CPU -> { /* Default */ }
            DELEGATE_GPU -> {
                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                    baseOptionsBuilder.useGpu()
                } else {
                    imageClassifierListener?.onError("GPU is not supported on this device")
                }
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try {
            imageClassifier = ImageClassifier.createFromFileAndOptions(
                context,
                getModelNameForTaskApi(),
                optionsBuilder.build()
            )
        } catch (e: Exception) {
            imageClassifierListener?.onError("Image classifier failed to initialize. See error logs for details")
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        }
    }

    private fun setupInterpreter() {
        val interpreterOptions = Interpreter.Options().setNumThreads(numThreads)
        when (currentDelegate) {
            DELEGATE_CPU -> { /* Default */ }
            DELEGATE_GPU -> {
                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                    interpreterOptions.addDelegate(GpuDelegate())
                } else {
                    imageClassifierListener?.onError("GPU is not supported on this device")
                }
            }
            DELEGATE_NNAPI -> {
                interpreterOptions.setUseNNAPI(true)
            }
        }

        try {
            interpreter = Interpreter(FileUtil.loadMappedFile(context, getModelNameForInterpreter()), interpreterOptions)
            labels = FileUtil.loadLabels(context, "converted_tflite/labels.txt")
            val inputTensor = interpreter!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            // NHWC: [1, height, width, channels]
            inputTensorHeight = inputShape[1]
            inputTensorWidth = inputShape[2]
        } catch (e: IOException) {
            imageClassifierListener?.onError("Failed to initialize interpreter: ${e.message}")
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        }
    }

    private fun getModelNameForTaskApi(): String {
        return when (currentModel) {
            MODEL_MOBILENETV1 -> "mobilenetv1.tflite"
            MODEL_EFFICIENTNETV0 -> "efficientnet-lite0.tflite"
            MODEL_EFFICIENTNETV1 -> "efficientnet-lite1.tflite"
            MODEL_EFFICIENTNETV2 -> "efficientnet-lite2.tflite"
            else -> "mobilenetv1.tflite"
        }
    }

    private fun getModelNameForInterpreter(): String {
        return "converted_tflite/model_unquant.tflite"
    }

    fun classify(image: Bitmap, rotation: Int) {
        if (interpreter == null && imageClassifier == null) {
            setupImageClassifier()
        }

        val startTime = SystemClock.uptimeMillis()
        if (currentModel == MODEL_UNQUANT) {
            classifyWithInterpreter(image, startTime)
        } else {
            classifyWithTaskApi(image, rotation, startTime)
        }
    }

    private fun classifyWithTaskApi(image: Bitmap, rotation: Int, startTime: Long) {
        if (imageClassifier == null) return
        val imageProcessor = ImageProcessor.Builder().build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setOrientation(getOrientationFromRotation(rotation))
            .build()

        val results = imageClassifier?.classify(tensorImage, imageProcessingOptions)
        val inferenceTime = SystemClock.uptimeMillis() - startTime
        imageClassifierListener?.onResults(results, inferenceTime)
    }

    private fun classifyWithInterpreter(image: Bitmap, startTime: Long) {
        if (interpreter == null) return

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputTensorHeight, inputTensorWidth, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(NormalizeOp(0.0f, 255.0f))
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val outputTensor = interpreter!!.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())

        interpreter?.run(tensorImage.buffer, outputBuffer.buffer)
        val inferenceTime = SystemClock.uptimeMillis() - startTime

        val tensorLabel = TensorLabel(labels, outputBuffer)
        val categoryList = tensorLabel.categoryList
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(maxResults)

        // Report interpreter results directly as support.label.Category list
        imageClassifierListener?.onInterpreterResults(categoryList, inferenceTime)
    }

    private fun getOrientationFromRotation(rotation: Int): ImageProcessingOptions.Orientation {
        return when (rotation) {
            Surface.ROTATION_270 -> ImageProcessingOptions.Orientation.BOTTOM_RIGHT
            Surface.ROTATION_180 -> ImageProcessingOptions.Orientation.RIGHT_BOTTOM
            Surface.ROTATION_90 -> ImageProcessingOptions.Orientation.TOP_LEFT
            else -> ImageProcessingOptions.Orientation.RIGHT_TOP
        }
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(results: List<Classifications>?, inferenceTime: Long)
        fun onInterpreterResults(categories: List<org.tensorflow.lite.support.label.Category>?, inferenceTime: Long)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_MOBILENETV1 = 0
        const val MODEL_EFFICIENTNETV0 = 1
        const val MODEL_EFFICIENTNETV1 = 2
        const val MODEL_EFFICIENTNETV2 = 3
        const val MODEL_UNQUANT = 4

        private const val TAG = "ImageClassifierHelper"
    }
}
