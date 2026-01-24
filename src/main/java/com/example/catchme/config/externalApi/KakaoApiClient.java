package com.example.catchme.config.externalApi;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

// 카카오 API 명세를 인터페이스로 정의
@HttpExchange("/v2/local/search")
public interface KakaoApiClient {

    @GetExchange("/keyword.json")
    Map<String, Object> searchKeyword(
            @RequestParam("query") String query,
            @RequestParam("x") double lng,
            @RequestParam("y") double lat,
            @RequestParam("radius") int radius,
            @RequestParam("sort") String sort
    );
}
