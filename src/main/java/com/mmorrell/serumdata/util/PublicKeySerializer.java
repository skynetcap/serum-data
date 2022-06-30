package com.mmorrell.serumdata.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
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

//    private static String getColorAsWebColor(Color color) {
//        int r = (int) Math.round(color.getRed() * 255.0);
//        int g = (int) Math.round(color.getGreen() * 255.0);
//        int b = (int) Math.round(color.getBlue() * 255.0);
//        return String.format("#%02x%02x%02x", r, g, b);
//    }
}
