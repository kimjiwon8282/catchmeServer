package com.example.catchme.config.auth;

import com.example.catchme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Spring Security가 인증 과정에서 호출하는 메서드
     * - username 파라미터에는 우리가 정의한 "email"이 들어옴
     */
    @Override
    public UserDetails loadUserByUsername(String email) //탈퇴 유저는 여기서 바로 컷
            throws UsernameNotFoundException {

        return userRepository.findByEmailAndWithdrawnFalse(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + email)
                );
    }
}
