package com.example.catchme.config.auth;

import com.example.catchme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Spring Security가 인증 과정에서 호출하는 메서드
     * - username 파라미터에는 우리가 정의한 "email"이 들어옴
     */
    @Override
    @Cacheable(value = "userCache", key = "#email", cacheManager = "redisCacheManager")
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        UserDetails userDetails = userRepository.findByEmailAndWithdrawnFalse(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + email)
                );

        return userDetails;
    }
}
