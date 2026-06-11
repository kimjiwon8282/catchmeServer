package com.example.catchme.service.impl.user;

import com.example.catchme.config.auth.MemberAuthLookupService;
import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.exception.exceptions.InvalidPasswordException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Member;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.service.interfaces.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberAuthLookupService memberAuthLookupService;

    @Override
    @Transactional
    public void updateName(Long userId, NameUpdateRequest request) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        member.updateName(request.getName());
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, PasswordUpdateRequest request) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new InvalidPasswordException("비밀번호가 올바르지 않습니다.");
        }

        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
        memberAuthLookupService.evictAfterCommit(userId);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        member.withdraw();
        memberAuthLookupService.evictAfterCommit(userId);
    }
}
