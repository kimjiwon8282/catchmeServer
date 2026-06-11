package com.example.catchme.controller;

import com.example.catchme.dto.QrLinkConnectRequest;
import com.example.catchme.dto.QrLinkTokenResponse;
import com.example.catchme.config.auth.MemberPrincipal;
import com.example.catchme.service.interfaces.user.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @AuthenticationPrincipal MemberPrincipal principal
    ){
        return linkService.generateQrToken(principal.getMemberId());
    }

    /**
     * GUARDIAN → QR 스캔 후 연동
     */
    @PostMapping("/connect")
    public ResponseEntity<Void> connect(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody QrLinkConnectRequest linkToken
    ) {
        linkService.connectByQr(principal.getMemberId(), linkToken.getLinkToken());
        return ResponseEntity.ok().build();
    }
}
