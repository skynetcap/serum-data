package com.mmorrell.serumdata.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@JsonComponent
public class PublicKeySerializer extends JsonSerializer<PublicKey> {

    @Override
    public void serialize(PublicKey publicKey, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) throws IOException {

        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(
                "publicKey",
                publicKey.toBase58()
        );
        jsonGenerator.writeEndObject();
    }
}
