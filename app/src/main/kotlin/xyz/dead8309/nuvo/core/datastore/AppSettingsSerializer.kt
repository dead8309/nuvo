package xyz.dead8309.nuvo.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import xyz.dead8309.nuvo.datastore.proto.AppSettingsProto
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class AppSettingsSerializer @Inject constructor() : Serializer<AppSettingsProto> {
    override val defaultValue: AppSettingsProto = AppSettingsProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppSettingsProto = try {
        AppSettingsProto.parseFrom(input)
    } catch (ex: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read proto.", ex)
    }

    override suspend fun writeTo(t: AppSettingsProto, output: OutputStream) {
        t.writeTo(output)
    }
}