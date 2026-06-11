package com.example.catchme.service.impl.rawData;

import com.example.catchme.model.Member;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RawDataMetadataServiceImpl implements RawDataMetadataService {

    private final RawDataFileRepository rawDataFileRepository;

    @Transactional
    public void save(Member member, String objectKey) {
        RawDataFile rawDataFile = RawDataFile.create(member, objectKey);
        rawDataFileRepository.save(rawDataFile);
    }
}
