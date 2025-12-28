package com.example.catchme.service.impl.user;

import com.example.catchme.dto.QrLinkTokenResponse;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.user.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LinkServiceImpl implements LinkService {
    private final UserRepository userRepository;

    private final Map<String,Long> tokenStore = new ConcurrentHashMap<>();

    /**
     * 환자(USER)가 QR 토큰 생성
     */
    @Override
    public QrLinkTokenResponse generateQrToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getRole() != Role.USER) {
            throw new IllegalStateException("환자만 QR을 생성할 수 있습니다.");
        }

        if (user.getLinkedUser() != null) {
            throw new IllegalStateException("이미 보호자와 연동된 계정입니다.");
        }

        String token = UUID.randomUUID().toString();
        tokenStore.put(token, user.getId());

        return new QrLinkTokenResponse(token);
    }

    /**
     * 보호자(GUARDIAN)가 QR 토큰으로 연동
     */
    @Override
    @Transactional
    public void connectByQr(Long guardianId, String linkToken) {
        Long userId = tokenStore.get(linkToken);

        if (userId == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 QR 토큰입니다.");
        }

        User guardian = userRepository.findById(guardianId)
                .orElseThrow(() -> new UserNotFoundException("보호자를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("환자를 찾을 수 없습니다."));

        // Role 검증
        if (guardian.getRole() != Role.GUARDIAN) {
            throw new IllegalStateException("보호자만 연동할 수 있습니다.");
        }

        if (user.getRole() != Role.USER) {
            throw new IllegalStateException("QR 대상이 환자가 아닙니다.");
        }

        // 중복 연동 방지
        if (guardian.getLinkedUser() != null || user.getLinkedUser() != null) {
            throw new IllegalStateException("이미 연동된 계정이 존재합니다.");
        }

        // 🔥 1:1 연동
        guardian.setLinkedUser(user);
        user.setLinkedUser(guardian);

        // 1회용 토큰 제거
        tokenStore.remove(linkToken);
    }
}
