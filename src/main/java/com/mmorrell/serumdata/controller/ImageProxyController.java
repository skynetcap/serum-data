package com.mmorrell.serumdata.controller;

import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.Token;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.Optional;

@RestController
public class ImageProxyController {

    private final TokenManager tokenManager;

    public ImageProxyController(final TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @GetMapping(value = "/api/token/icon/{tokenId}")
    @ResponseBody
    public ResponseEntity<InputStreamResource> getIconByTokenMint(@PathVariable String tokenId) {
        PublicKey tokenMint = new PublicKey(tokenId);
        Optional<Token> optionalToken = tokenManager.getTokenByMint(tokenMint);
        if (optionalToken.isPresent()) {
            Token token = optionalToken.get();
            InputStreamResource tokenImage = tokenManager.getTokenImageInputStream(token);
            MediaType contentType = switch (token.getImageFormat()) {
                case "png" -> MediaType.IMAGE_PNG;
                case "gif" -> MediaType.IMAGE_GIF;
                case "svg" -> MediaType.valueOf("image/svg+xml");
                default -> MediaType.IMAGE_JPEG;
            };

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(tokenImage);
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(new InputStreamResource(new ByteArrayInputStream(tokenManager.getPlaceHolderImage())));
        }
    }

}