package com.mmorrell.serumdata.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.util.Base64;

@JsonComponent
public class Base64Serializer extends JsonSerializer<byte[]> {

    @Override
    public void serialize(byte[] data, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(Base64.getEncoder().encodeToString(data));
    }
}
