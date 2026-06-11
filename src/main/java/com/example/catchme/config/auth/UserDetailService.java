package com.example.catchme.config.auth;

import com.example.catchme.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailService implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * Spring Security가 인증 과정에서 호출하는 메서드
     * - username 파라미터에는 우리가 정의한 "email"이 들어옴
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        return memberRepository.findByEmail(email)
                .filter(member -> !member.isWithdrawn())
                .map(member -> new MemberLoginDetails(
                        member.getId(),
                        member.getEmail(),
                        member.getPassword(),
                        member.getRole(),
                        !member.isWithdrawn()
                ))
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + email)
                );
    }
}
