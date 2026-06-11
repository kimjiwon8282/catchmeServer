package com.example.catchme.repository;

import com.example.catchme.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.linkedMember WHERE m.id = :id")
    Optional<Member> findByIdWithLinkedMember(@Param("id") Long id);
}
