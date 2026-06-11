package com.example.catchme.config.auth;

import com.example.catchme.model.Member;
import com.example.catchme.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class MemberAuthLookupService {

    private final MemberRepository memberRepository;
    private final CacheManager redisCacheManager;

    @Cacheable(cacheNames = "memberAuthCache", key = "#memberId", cacheManager = "redisCacheManager")
    public MemberAuthCacheDto getMemberAuth(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException("Member not found: " + memberId));

        if (member.isWithdrawn()) {
            throw new DisabledException("Withdrawn member: " + memberId);
        }

        return new MemberAuthCacheDto(
                member.getId(),
                member.getEmail(),
                member.getRole(),
                true
        );
    }

    public void evict(Long memberId) {
        Cache cache = redisCacheManager.getCache("memberAuthCache");
        if (cache != null) {
            cache.evict(memberId);
        }
    }

    public void evictAfterCommit(Long memberId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(memberId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(memberId);
            }
        });
    }
}
