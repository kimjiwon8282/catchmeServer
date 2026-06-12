package com.example.catchme.service.impl.user;

import com.example.catchme.dto.QrLinkTokenResponse;
import com.example.catchme.exception.exceptions.QrServiceUnavailableException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Member;
import com.example.catchme.model.Role;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.service.interfaces.user.LinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkServiceImpl implements LinkService {

    private static final String QR_KEY_PREFIX = "QR:LINK:";
    private static final Duration QR_EXPIRATION = Duration.ofMinutes(10);
    private static final String QR_UNAVAILABLE_MESSAGE = "QR 연동 기능을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.";

    private final MemberRepository memberRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public QrLinkTokenResponse generateQrToken(Long userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (member.getLinkedMember() != null) {
            throw new IllegalStateException("이미 보호자와 연결된 계정입니다.");
        }

        String token = UUID.randomUUID().toString();
        String key = QR_KEY_PREFIX + token;

        try {
            redisTemplate.opsForValue().set(key, String.valueOf(member.getId()), QR_EXPIRATION);
        } catch (DataAccessException e) {
            throw qrUnavailable("QR_CREATE", e);
        }

        return new QrLinkTokenResponse(token);
    }

    @Override
    @Transactional
    public void connectByQr(Long guardianId, String linkToken) {
        String key = QR_KEY_PREFIX + linkToken;

        String userIdStr;
        try {
            userIdStr = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            throw qrUnavailable("QR_CONNECT", e);
        }

        if (userIdStr == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 QR 토큰입니다.");
        }

        Long userId = Long.parseLong(userIdStr);
        List<Member> members = memberRepository.findAllById(List.of(guardianId, userId));

        if (members.size() < 2) {
            throw new UserNotFoundException("사용자 또는 보호자 정보를 찾을 수 없습니다.");
        }

        Map<Long, Member> memberMap = members.stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        Member guardian = memberMap.get(guardianId);
        Member member = memberMap.get(userId);

        if (member.getRole() != Role.USER) {
            throw new IllegalStateException("QR 대상이 사용자가 아닙니다.");
        }

        if (guardian.getLinkedMember() != null || member.getLinkedMember() != null) {
            throw new IllegalStateException("이미 연결된 계정이 존재합니다.");
        }

        guardian.setLinkedMember(member);
        member.setLinkedMember(guardian);

        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            throw qrUnavailable("QR_DELETE", e);
        }
    }

    private QrServiceUnavailableException qrUnavailable(String feature, DataAccessException e) {
        log.warn(
                "QR Redis operation failed. feature={}, exception={}, message={}, convertedTo503=true",
                feature,
                e.getClass().getSimpleName(),
                e.getMessage()
        );
        return new QrServiceUnavailableException(QR_UNAVAILABLE_MESSAGE, e);
    }
}
