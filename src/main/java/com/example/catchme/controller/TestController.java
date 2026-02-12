package com.example.catchme.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    // 로그인한 유저만 통과 가능한지 확인하는 용도
    // 로직 비용 0에 수렴함. 오직 필터(DB조회) 비용만 측정 가능.
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
