package com.example.catchme.repository;

import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RawDataFileRepository extends JpaRepository<RawDataFile, Long> {
    Optional<RawDataFile> findTopByUserOrderByCreatedAtDesc(User user);
} //유저 기준, createdAt 최신 1건
