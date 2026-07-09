package com.candorfoods.factoryops.data.repository

import android.content.Context
import android.net.Uri
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.MachineTransferApi
import com.candorfoods.factoryops.data.remote.dto.MachineTransferEditDto
import com.candorfoods.factoryops.data.remote.dto.MachineTransferListItemDto
import com.candorfoods.factoryops.domain.model.MachineTransfer
import com.candorfoods.factoryops.domain.model.NewMachineTransfer
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineTransferRepository
import com.candorfoods.factoryops.util.ImageCompressor
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

            // Optional proof image -> a "proof_photo" file part. Downscale + re-encode to
            // a small JPEG first so the upload is fast (full-res phone photos are several
            // MB); fall back to the raw bytes if it can't be decoded.
            val photoPart: MultipartBody.Part? = input.photoUri?.let { uriStr ->
                val uri = Uri.parse(uriStr)
                val compressed = ImageCompressor.compressToJpeg(context, uri)
                val bytes: ByteArray
                val mime: String
                val ext: String
                if (compressed != null) {
                    bytes = compressed; mime = "image/jpeg"; ext = "jpg"
                } else {
                    // couldn't decode -> send original, but keep the type/size guards
                    val rawMime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    if (rawMime != "image/jpeg" && rawMime != "image/png") {
                        return Result.Error("Proof photo must be a JPEG or PNG image.")
                    }
                    bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return Result.Error("Couldn't read the selected photo.")
                    mime = rawMime
                    ext = if (rawMime == "image/png") "png" else "jpg"
                }
                if (bytes.size > MAX_PHOTO_BYTES) {
                    return Result.Error("Proof photo must be 10 MB or smaller.")
                }
                MultipartBody.Part.createFormData(
                    "proof_photo", "proof_photo.$ext", bytes.toRequestBody(mime.toMediaType())
                )
            }

            val resp = api.create(
                token = token,
                fromWarehouse = input.fromWarehouse.textPart(),
                toWarehouse = input.toWarehouse.textPart(),
                machineName = input.machineName.textPart(),
                assetId = input.assetId?.textPart(),
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

    override suspend fun acknowledgeTransfer(id: Int): Result<MachineTransfer> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(api.acknowledge(token, id).toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to acknowledge transfer", e)
        }
    }

    override suspend fun updateTransfer(id: Int, input: NewMachineTransfer): Result<MachineTransfer> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            val dto = MachineTransferEditDto(
                fromWarehouse = input.fromWarehouse,
                toWarehouse = input.toWarehouse,
                machineName = input.machineName,
                assetId = input.assetId,
                date = input.date,
                machineCode = input.machineCode,
                condition = input.condition,
                reason = input.reason,
                authorisedPerson = input.authorisedPerson,
                remarks = input.remarks,
            )
            Result.Success(api.update(token, id, dto).toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to update transfer", e)
        }
    }

    override suspend fun deleteTransfer(id: Int): Result<Unit> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            val resp = api.delete(token, id)
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error("Couldn't delete transfer (HTTP ${resp.code()}).")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to delete transfer", e)
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
        machineCode = machineCode,
        reason = reason,
        authorisedPerson = authorisedPerson,
        remarks = remarks,
        createdAt = createdAt,
        proofPhotoUrl = proofPhotoUrl,
        status = status,
        acknowledgedBy = acknowledgedBy,
        acknowledgedAt = acknowledgedAt,
        canAcknowledge = canAcknowledge,
        canEdit = canEdit,
    )

    companion object {
        private const val MAX_PHOTO_BYTES = 10 * 1024 * 1024
        private val TEXT_PLAIN = "text/plain".toMediaType()
    }
}
