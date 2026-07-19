package com.example.pluck.data.files

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JourneyPhotoStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.filesDir, "journey_photos").apply { mkdirs() }
    fun newPhotoFile(): File = File(directory, "pluck_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.jpg")
}
