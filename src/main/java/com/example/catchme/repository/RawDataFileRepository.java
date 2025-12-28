package com.example.catchme.repository;

import com.example.catchme.model.RawDataFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawDataFileRepository extends JpaRepository<RawDataFile, Long> {
}
