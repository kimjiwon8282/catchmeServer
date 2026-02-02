package com.example.catchme.repository;

import com.example.catchme.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndWithdrawnFalse(String email);
    boolean existsByEmail(String email); //select 1 from users where email = ? limit 1

    // 유저를 가져올 때 보호자(linkedUser)도 '한 방'에 데려온다.
    // LEFT JOIN: 보호자가 없는 환자도 조회되어야 하므로 필수
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.linkedUser WHERE u.id = :id")
    Optional<User> findByIdWithLinkedUser(@Param("id") Long id);
}
