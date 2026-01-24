package com.example.catchme.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LocationRequest {

    @NotNull(message = "위도(lat)는 필수입니다.")
    @Min(value = 33, message = "위도는 33 이상이어야 합니다.")
    @Max(value = 43, message = "위도는 43 이하여야 합니다.")
    private Double lat;

    @NotNull(message = "경도(lng)는 필수입니다.")
    @Min(value = 124, message = "경도는 124 이상이어야 합니다.")
    @Max(value = 132, message = "경도는 132 이하여야 합니다.")
    private Double lng;
}
