package com.candorfoods.factoryops.data.repository

import android.content.Context
import android.net.Uri
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.MachineTransferApi
import com.candorfoods.factoryops.data.remote.dto.MachineTransferListItemDto
import com.candorfoods.factoryops.domain.model.MachineTransfer
import com.candorfoods.factoryops.domain.model.NewMachineTransfer
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineTransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends machine transfers to the backend as multipart/form-data. The image is
 * streamed as a file part read from the picked content:// uri via ContentResolver —
 * NO AWS SDK and NO credentials on the device; the backend owns S3 and returns the
 * public URL. Online-only: a failure surfaces as Result.Error (transfers are rare and
 * need the server id + URL, so there is no offline queue).
 */
@Singleton
class MachineTransferRepositoryImpl @Inject constructor(
    private val api: MachineTransferApi,
    private val authPreferences: AuthPreferences,
    @ApplicationContext private val context: Context,
) : MachineTransferRepository {

    override suspend fun createTransfer(input: NewMachineTransfer): Result<MachineTransfer> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"

            // Optional proof image -> a "proof_photo" file part. Validate type/size up
            // front so we fail with a clear message instead of a backend 400.
            val photoPart: MultipartBody.Part? = input.photoUri?.let { uriStr ->
                val uri = Uri.parse(uriStr)
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                if (mime != "image/jpeg" && mime != "image/png") {
                    return Result.Error("Proof photo must be a JPEG or PNG image.")
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return Result.Error("Couldn't read the selected photo.")
                if (bytes.size > MAX_PHOTO_BYTES) {
                    return Result.Error("Proof photo must be 10 MB or smaller.")
                }
                val ext = if (mime == "image/png") "png" else "jpg"
                MultipartBody.Part.createFormData(
                    "proof_photo", "proof_photo.$ext", bytes.toRequestBody(mime.toMediaType())
                )
            }

            val resp = api.create(
                token = token,
                fromWarehouse = input.fromWarehouse.textPart(),
                toWarehouse = input.toWarehouse.textPart(),
                machineName = input.machineName.textPart(),
                date = input.date?.textPart(),
                machineCode = input.machineCode?.textPart(),
                condition = input.condition?.textPart(),
                reason = input.reason?.textPart(),
                authorisedPerson = input.authorisedPerson?.textPart(),
                remarks = input.remarks?.textPart(),
                proofPhoto = photoPart,
            )
            Result.Success(
                MachineTransfer(
                    id = resp.id,
                    fromWarehouse = input.fromWarehouse,
                    toWarehouse = input.toWarehouse,
                    machineName = input.machineName,
                    date = input.date,
                    condition = input.condition,
                    proofPhotoUrl = resp.proofPhotoUrl,
                )
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to submit transfer", e)
        }
    }

    override suspend fun listTransfers(): Result<List<MachineTransfer>> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(api.list(token).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load transfers", e)
        }
    }

    private fun String.textPart(): RequestBody = toRequestBody(TEXT_PLAIN)

    private fun MachineTransferListItemDto.toDomain() = MachineTransfer(
        id = id,
        fromWarehouse = fromWarehouse,
        toWarehouse = toWarehouse,
        machineName = machineName,
        date = date,
        condition = condition,
        createdAt = createdAt,
        proofPhotoUrl = proofPhotoUrl,
    )

    companion object {
        private const val MAX_PHOTO_BYTES = 10 * 1024 * 1024
        private val TEXT_PLAIN = "text/plain".toMediaType()
    }
}
