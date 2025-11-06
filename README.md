```markdown
# Panduan Mudah Deploy TFLite untuk Android (untuk siswa SMK)

Tujuan: Biar proyek Androidmu yang pakai model .tflite bisa dibuild dan dijalankan di HP menggunakan Android Studio versi baru. Panduan ini dibuat karena contoh di tensorflow/examples pakai versi dependensi lama.

--------------------------
Ringkasan langkah singkat:
1. Export model dari Teachable Machine (pilih TensorFlow Lite).
2. Taruh model hasil export ke folder app/src/main/assets/.
3. Perbarui dependency TFLite di app/build.gradle.
4. Tambah kode untuk memuat model (contoh ada di bawah).
5. Build dan install ke HP dengan `./gradlew assembleDebug` dan `adb install`.

--------------------------
1) Ekspor model dari Teachable Machine
- Buka proyek di Teachable Machine.
- Klik "Export" → pilih tab "TensorFlow Lite".
- Pilih jenis konversi:
  - Floating point: ukuran lebih besar, akurasi original.
  - Quantized: ukuran kecil dan lebih cepat di HP (direkomendasikan untuk mobile).
  - EdgeTPU: untuk Coral (biasanya bukan untuk HP biasa).
- Klik "Download my model". Kamu akan mendapatkan file zip (mis. converted_tflite_quantized.zip atau converted_tflite.zip).

Gambar yang dicontohkan di Teachable Machine:
- Pilih TensorFlow Lite → pilih "Quantized" jika mau ukuran kecil → Download my model.
(Ini sama seperti gambar yang kamu kirim.)

2) Buka hasil export dan salin file model ke proyek
- Unzip file yang kamu download.
- Biasanya di dalam folder ada:
  - model.tflite
  - labels.txt (atau files/labels.txt)
  - metadata.json (opsional)
- Cara paling mudah: copy `model.tflite` dan `labels.txt` ke:
  `app/src/main/assets/`
  Jika kamu lebih suka folder, bisa juga copy folder `converted_tflite` ke `app/src/main/assets/converted_tflite/` sehingga path model jadi `assets/converted_tflite/model.tflite`.

Catatan: Pastikan nama file pas dan huruf besar/kecil sama.

3) Update dependency TensorFlow Lite (di app/build.gradle)
Tambahkan dependency TFLite agar bisa pakai Interpreter:
- Contoh:
  implementation 'org.tensorflow:tensorflow-lite:2.11.0'
- Jika mau GPU (opsional):
  implementation 'org.tensorflow:tensorflow-lite-gpu:2.11.0'

4) Contoh kode sederhana untuk memuat model (Kotlin)
- Contoh fungsi untuk load model dari assets:
```kotlin
import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

fun loadModelFile(assetManager: AssetManager, fileName: String): MappedByteBuffer {
    val fd = assetManager.openFd(fileName)
    val input = FileInputStream(fd.fileDescriptor)
    val channel = input.channel
    return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
}

// Cara pakai:
val modelBuffer = loadModelFile(context.assets, "model.tflite") // atau "converted_tflite/model.tflite"
val tflite = Interpreter(modelBuffer)
```

5) Jika kamu pakai kode dari contoh Teachable Machine (modifikasi getModelPath/getLabelPath)
- Di beberapa contoh, kamu perlu ubah fungsi getModelPath() dan getLabelPath() supaya menunjuk ke nama file yang benar (mis. "model.tflite" dan "labels.txt") atau ke folder "converted_tflite/model.tflite".
- Contoh:
    - getModelPath() -> return "model.tflite"
    - getLabelPath() -> return "labels.txt"

6) Build & install ke HP
- Di terminal (folder root project, tempat gradlew):
    - Build debug:
      ./gradlew assembleDebug
    - Install ke HP (HP harus terhubung, USB Debugging ON):
      adb install -r app/build/outputs/apk/debug/app-debug.apk
- Atau klik Run di Android Studio langsung.

7) Masalah versi Gradle / Android Gradle Plugin (AGP)
- Jika Android Studio kamu lebih baru, kamu mungkin perlu:
    - Update Gradle wrapper ke versi yang cocok.
    - Update plugin Android Gradle (di root build.gradle) supaya sesuai dengan Gradle wrapper.
- Contoh pairing (cek error message kalau perlu):
    - AGP 7.4 → Gradle 7.5
    - AGP 8.x → Gradle 8.x
- Cara mudah: biarkan Android Studio melakukan "Sync" dan ikuti saran update yang muncul, atau jalankan:
  gradle wrapper --gradle-version 7.5
  (Jalankan ini hanya kalau punya Gradle di komputer.)

8) ProGuard (kalau pakai minify)
   Tambahkan ini di proguard-rules.pro:
```
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
```

9) Troubleshooting mudah (cek kalau error)
- Error "model not found": cek lagi `app/src/main/assets/` apakah file ada.
- Nama file salah: periksa kapitalisasi.
- Error runtime: buka Logcat di Android Studio untuk pesan detail.
- Model terlalu besar / lambat: coba export quantized dari Teachable Machine lalu tes lagi.

10) Opsi otomatis (jika kamu pakai download_models.gradle)
- Pastikan task itu menaruh `model.tflite` ke `app/src/main/assets/` sebelum assemble. Kalau tidak, assemble tidak memasukkan model ke APK.

--------------------------
Cheat-sheet cepat:
1. Export dari Teachable Machine → pilih TensorFlow Lite (quantized rekomendasi).
2. Unzip, copy model.tflite & labels.txt ke app/src/main/assets/.
3. Update dependency TFLite di app/build.gradle.
4. Build `./gradlew assembleDebug`.
5. Install `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
6. Cek Logcat kalau ada error.

--------------------------
Catatan tambahannya:
- Pilih quantized kalau mau ukuran kecil dan performa lebih baik di HP.
- Pastikan project punya `gradlew` dan `settings.gradle` supaya mudah build di CI atau komputer lain.
- Kalau mau, minta guru bantu cek pairing versi Gradle/AGP jika Android Studio memunculkan error versi.
```