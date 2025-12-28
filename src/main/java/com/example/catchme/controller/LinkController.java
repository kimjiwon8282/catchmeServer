package com.example.catchme.controller;

import com.example.catchme.dto.QrLinkConnectRequest;
import com.example.catchme.dto.QrLinkTokenResponse;
import com.example.catchme.model.User;
import com.example.catchme.service.interfaces.user.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/link")
@RequiredArgsConstructor
public class LinkController {
    private final LinkService linkService;

    /**
     * USER → QR 토큰 생성
     */
    @PostMapping("/qr")
    public QrLinkTokenResponse generateQr(
            @AuthenticationPrincipal User user
    ){
        return linkService.generateQrToken(user.getId());
    }

    /**
     * GUARDIAN → QR 스캔 후 연동
     */
    @PostMapping("/connect")
    public ResponseEntity<Void> connect(
            @AuthenticationPrincipal User user,
            @RequestBody QrLinkConnectRequest linkToken
    ) {
        linkService.connectByQr(user.getId(), linkToken.getLinkToken());
        return ResponseEntity.ok().build();
    }
}
