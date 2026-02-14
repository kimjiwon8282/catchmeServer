package com.example.catchme.service.impl.user;

import com.example.catchme.dto.QrLinkTokenResponse;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.user.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LinkServiceImpl implements LinkService {
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;// ⚡ ConcurrentHashMap 대신 Redis 사용
    private static final String QR_KEY_PREFIX = "QR:LINK:"; // Redis Key 구분자
    private static final Duration QR_EXPIRATION = Duration.ofMinutes(10); // QR 유효시간 10분


    /**
     * 환자(USER)가 QR 토큰 생성
     */
    @Override
    public QrLinkTokenResponse generateQrToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getLinkedUser() != null) {
            throw new IllegalStateException("이미 보호자와 연동된 계정입니다.");
        }

        // 1. 토큰 생성
        String token = UUID.randomUUID().toString();
        String key = QR_KEY_PREFIX + token;

        // 2. Redis에 저장 (Key: 토큰, Value: userId, TTL: 10분)
        // -> ConcurrentHashMap.put() 대체
        redisTemplate.opsForValue().set(key, String.valueOf(user.getId()), QR_EXPIRATION);

        return new QrLinkTokenResponse(token);
    }

    /**
     * 보호자(GUARDIAN)가 QR 토큰으로 연동
     */
    @Override
    @Transactional
    public void connectByQr(Long guardianId, String linkToken) {
        String key = QR_KEY_PREFIX + linkToken;

        String userIdStr = redisTemplate.opsForValue().get(key);

        if (userIdStr == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 QR 토큰입니다.");
        }
        Long userId = Long.parseLong(userIdStr);
        // ⚡ [DB 최적화] SELECT * FROM users WHERE id IN (?, ?)
        // 네트워크 왕복(Round Trip)을 2회 -> 1회로 단축
        List<User> users = userRepository.findAllById(List.of(guardianId,userId));

        if (users.size() < 2) {
            throw new UserNotFoundException("환자 또는 보호자 정보를 찾을 수 없습니다.");
        }
        // List를 Map<ID, User>로 변환하여 빠르게 찾기
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        User guardian = userMap.get(guardianId);
        User user = userMap.get(userId);

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

        // 5. 사용된 토큰 삭제 (재사용 방지)
        // -> ConcurrentHashMap.remove() 대체
        redisTemplate.delete(key);
    }
}
