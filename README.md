```markdown
# Panduan Mudah Deploy TFLite untuk Android (untuk siswa SMK)

Versi singkat: Ini panduan langkah demi langkah supaya proyek Androidmu yang pakai model .tflite bisa dibuild dan dijalankan di HP. Panduan ini dibuat karena contoh dari tensorflow/examples punya versi dependensi yang lama dan tidak cocok dengan Android Studio yang lebih baru.

---

## 1. Hal yang harus disiapkan dulu
- Android Studio (pakai versi terbaru yang kamu punya).
- Java JDK (disarankan JDK 11).
- Perangkat Android (HP) atau emulator.
- File model `.tflite` (mis. `model.tflite`) — kalau belum ada, taruh di folder assets nanti.
- Pastikan project kamu ada folder `app/` dengan `src/main/...`. Kalau belum ada, buat sesuai contoh di bawah.

---

## 2. Struktur project yang disarankan
Buat struktur sederhana seperti ini:
- / (root repo)
  - gradlew
  - gradlew.bat
  - gradle/
  - settings.gradle
  - build.gradle (root)
  - gradle.properties
  - app/
    - build.gradle
    - src/
      - main/
        - java/...
        - res/...
        - assets/  <- taruh model.tflite di sini

Jika belum ada gradle wrapper (gradlew), buka Android Studio lalu pilih menu untuk "Sync Project" → Android Studio biasanya bisa menambahkan wrapper otomatis. Atau jalankan di mesin dengan Gradle: `gradle wrapper --gradle-version 7.5` (contoh).

---

## 3. Memasukkan model (.tflite)
Cara paling mudah:
1. Buka folder `app/src/main/assets/`. Jika belum ada, buat folder `assets`.
2. Salin `model.tflite` ke `app/src/main/assets/model.tflite`.

Catatan: Nama file sensitif huruf besar/kecil — jangan salah tulis.

---

## 4. Update dependency TensorFlow Lite
Di `app/build.gradle` tambahkan baris ini di `dependencies`:
```groovy
implementation 'org.tensorflow:tensorflow-lite:2.11.0' // contoh versi, cek versi terbaru kalau perlu
// Jika mau pakai GPU delegate:
// implementation 'org.tensorflow:tensorflow-lite-gpu:2.11.0'
```
Kalau ada error versi, ubah versi `org.tensorflow` ke versi yang cocok dengan repository Maven (cek di https://mvnrepository.com atau di dokumentasi TFLite).

---

## 5. Contoh kode sederhana untuk memuat model (Kotlin)
Masukkan kode berikut di Activity atau class Kotlin kamu:
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

// Penggunaan:
val modelBuffer = loadModelFile(context.assets, "model.tflite")
val tflite = Interpreter(modelBuffer)
```
Penjelasan gampang:
- `assets` = tempat file model dimasukkan ke aplikasi.
- `Interpreter` = alat untuk menjalankan model dan mendapat hasil prediksi.

---

## 6. Build dan install ke HP
Buka terminal di folder root (tempat `gradlew` berada), jalankan:
- Build debug:
```
./gradlew assembleDebug
```
- Install ke HP (HP harus aktif USB Debugging dan terhubung):
```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Jika Android Studio yang kamu pakai bisa langsung run, klik Run → pilih device.

---

## 7. Kalau ada masalah karena versi Gradle / Android Gradle Plugin
Masalah yang sering muncul:
- Contoh tensorflow menggunakan versi AGP (Android Gradle Plugin) lama. Kalau Android Studio kamu lebih baru, update `build.gradle` root agar AGP cocok.
  Contoh pairing umum:
- AGP 7.4.x → Gradle 7.5
- AGP 8.x → Gradle 8.x
  (Aturan detail berubah, jadi terbaik cek pesan error Gradle dan ikuti saran upgrade.)

Untuk mengubah gradle wrapper:
```
# jika punya gradle terinstall lokal:
gradle wrapper --gradle-version 7.5
```
Atau biarkan Android Studio menanyakan update wrapper saat sync.

---

## 8. Rules ProGuard (kalau pakai minify / release)
Tambahkan ke `proguard-rules.pro` supaya library TFLite tidak terhapus:
```
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
```

---

## 9. Periksa kalau model tidak ditemukan saat runtime
1. Pastikan `model.tflite` benar-benar ada di `assets` dalam APK (kamu bisa unzip file APK).
2. Lihat Logcat di Android Studio untuk pesan error.
3. Periksa nama file dan path `assets/model.tflite`.

---

## 10. Optimasi sederhana agar aplikasi cepat
- Kuantisasi model (8-bit) untuk mengecilkan ukuran dan mempercepat inferensi.
- Jika butuh performa lebih tinggi, pakai GPU delegate (tapi periksa kompatibilitas HP).
- Uji model di HP target — hasil bisa berbeda di perangkat berbeda.

---

## 11. Cheat-sheet langkah cepat
1. Pastikan `app/src/main/assets/model.tflite` ada.
2. Tambah dependency TFLite di `app/build.gradle`.
3. Tambah kode load model (lihat contoh).
4. Jalankan `./gradlew assembleDebug`.
5. Install ke HP: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
6. Buka app, cek hasil prediksi.

---