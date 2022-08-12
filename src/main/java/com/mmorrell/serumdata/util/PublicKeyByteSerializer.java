package com.mmorrell.serumdata.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@JsonComponent
public class PublicKeyByteSerializer extends JsonSerializer<byte[]> {

    @Override
    public void serialize(byte[] data, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) throws IOException {

        jsonGenerator.writeString(new PublicKey(data).toBase58());
    }
}
