package com.example.catchme.service.interfaces.user;

import com.example.catchme.dto.HospitalResponse;

import java.util.List;

public interface HospitalService {

    List<HospitalResponse> findNearbyHospitals(double lat, double lng);
}
