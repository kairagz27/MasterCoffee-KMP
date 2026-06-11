package kz.master_coffee.app

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun uploadImageToFirebase(byteArray: ByteArray, path: String): String {
    return suspendCancellableCoroutine { continuation ->
        // Получаем ссылку на хранилище
        val storageRef = FirebaseStorage.getInstance().reference.child(path)

        // Начинаем загрузку
        val uploadTask = storageRef.putBytes(byteArray)

        uploadTask.addOnSuccessListener {
            // КОГДА ЗАГРУЗКА УСПЕШНО ЗАВЕРШЕНА, ТОЛЬКО ТОГДА ПРОСИМ ССЫЛКУ!
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                continuation.resume(uri.toString()) // Возвращаем готовую ссылку
            }.addOnFailureListener { exception ->
                continuation.resumeWithException(exception) // Если не смогли получить ссылку
            }
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(exception) // Если сломалась сама загрузка
        }
    }
}