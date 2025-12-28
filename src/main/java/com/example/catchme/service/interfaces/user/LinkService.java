package com.example.catchme.service.interfaces.user;

import com.example.catchme.dto.QrLinkTokenResponse;

public interface LinkService {

    QrLinkTokenResponse generateQrToken(Long userId);

    void connectByQr(Long guardianId, String linkToken);
}

