package kz.master_coffee.app

expect suspend fun uploadImageToFirebase(byteArray: ByteArray, path: String): String