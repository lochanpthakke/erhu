package com.lochanpthakke.empee3.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import java.io.InputStream
import java.io.OutputStream

object PlaybackStateProtoSerializer : Serializer<PlaybackStateProto> {
    override val defaultValue: PlaybackStateProto = PlaybackStateProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): PlaybackStateProto =
        try {
            PlaybackStateProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read PlaybackStateProto", exception)
        }

    override suspend fun writeTo(
        t: PlaybackStateProto,
        output: OutputStream,
    ) {
        t.writeTo(output)
    }
}
