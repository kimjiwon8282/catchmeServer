package com.example.catchme.service.interfaces.rawData;

import com.example.catchme.model.Member;

public interface RawDataMetadataService {
    void save(Member member, String objectKey);
}
