package com.example.pluck.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class CapturedLocation(val latitude: Double, val longitude: Double, val address: String?)

class LocationCapture @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    @SuppressLint("MissingPermission")
    suspend fun current(): CapturedLocation? = suspendCancellableCoroutine { continuation ->
        val cancellationToken = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellationToken.cancel() }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (location == null) continuation.resume(null) else continuation.resume(CapturedLocation(location.latitude, location.longitude, null))
            }
            .addOnFailureListener {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { continuation.resume(it?.let { last -> CapturedLocation(last.latitude, last.longitude, null) }) }
                    .addOnFailureListener { continuation.resume(null) }
            }
    }?.let { location -> location.copy(address = reverseGeocode(location.latitude, location.longitude)) }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val address: Address? = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            address?.getAddressLine(0)
        }.getOrNull()
    }
}
