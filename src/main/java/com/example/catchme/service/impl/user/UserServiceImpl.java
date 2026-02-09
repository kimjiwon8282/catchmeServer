package com.example.catchme.service.impl.user;

import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.exception.exceptions.InvalidPasswordException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager; // ⭐️ @Primary가 붙은 RedisCacheManager가 주입됨
    @Override
    @Transactional
    public void updateName(Long userId, NameUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("사용자를 찾을 수 없습니다.")
                );

        user.updateName(request.getName());
        evictCache(user.getEmail());
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, PasswordUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("사용자를 찾을 수 없습니다.")
                );

        // 1️⃣ 현재 비밀번호 검증
        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPassword()
        )) {
            throw new InvalidPasswordException("비밀번호가 올바르지 않습니다.");
        }

        // 2️⃣ 새 비밀번호 암호화 & 변경
        user.changePassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        evictCache(user.getEmail());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("사용자를 찾을 수 없습니다.")
                );

        user.withdraw();

        evictCache(user.getEmail());
    }

    /**
     * 🗑️ Redis 캐시 삭제 도우미 메서드
     * - "userCache"라는 상자에서 email에 해당하는 데이터를 찾아서 지움
     */
    private void evictCache(String email) {
        try {
            // "userCache"는 UserDetailService에서 @Cacheable(value = "userCache")로 쓴 그 이름입니다.
            Objects.requireNonNull(cacheManager.getCache("userCache")).evict(email);
            log.info("🗑️ [Cache Evict] 사용자 정보 수정으로 캐시 삭제 완료 (email: {})", email);
        } catch (Exception e) {
            log.error("⚠️ [Cache Evict Error] 캐시 삭제 중 오류 발생 (email: {})", email, e);
        }
    }

}
