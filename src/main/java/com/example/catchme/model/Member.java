package com.example.catchme.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_user_id")
    private Member linkedMember;

    @Column(length = 500)
    private String fcmToken;

    @Column(nullable = false)
    private boolean withdrawn;

    private LocalDateTime withdrawnAt;

    @Builder
    public Member(String email, String password, String name, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.withdrawn = false;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void changePassword(String encode) {
        this.password = encode;
    }

    public void setLinkedMember(Member member) {
        this.linkedMember = member;
    }

    public void updateFcmToken(String token) {
        this.fcmToken = token;
    }

    public void withdraw() {
        this.withdrawn = true;
        this.withdrawnAt = LocalDateTime.now();
        this.fcmToken = null;
    }
}
