# CatchMe - 스마트 인솔 기반 보행 데이터 분석 및 신경계 질환 예측 플랫폼 API 서버

## 1. 프로젝트 개요 및 배경

### 1.1. 프로젝트 목적
스마트 인솔(Smart Insole)에서 수집된 족압 및 가속도 데이터를 기반으로 환자의 보행 패턴을 분석하고, 치매 및 뇌졸중, 파킨슨병 등 신경계 질환의 이상 징후를 조기 예측하여 보호자에게 실시간 알림을 제공하는 헬스케어 백엔드 시스템 구축임.

### 1.2. 추진 배경 및 문제 인식
* 치매 환자 및 사회적 비용 급증: 국내 65세 이상 치매 환자 수는 2015년 약 63만 명에서 2023년 약 98만 명으로 57% 증가하였으며, 국가 치매 관리 비용은 2021년 기준 18.7조 원에 육박함.
* 조기 진단의 한계: 초기 증상을 단순 노화로 오인하거나, 인지검사 비용 부담 및 부정적 인식으로 인해 병원 방문을 꺼리는 심리적 장벽이 존재함.

### 1.3. 솔루션 및 차별점
* 하드웨어 및 데이터 수집: 피에조 센서(발바닥 압력)와 가속도 센서(보폭 및 움직임)를 장착한 스마트 인솔을 통해 블루투스 5.0 기반으로 안정적인 보행 데이터를 수집함.
* 소프트웨어 및 예측: 수집된 보행 데이터를 K-means 클러스터링 알고리즘을 활용해 군집화(Clustering)하고, 정상 보행과 상이한 이상 보행 패턴(위험군)을 식별하여 뇌질환 가능성을 예측함.
* 경쟁사 대비 우위: 길온, 솔티드 인솔 등 기존 제품과 달리, 치매 가능성 알림 및 자가 문진표를 제공하며 의료 기관 연계 기능을 포함함.
* 아키텍처 설계 목표: 환자의 건강과 직결된 크리티컬 데이터를 다루므로 데이터 무결성 보장, 고가용성(High Availability) 확보, 대규모 트래픽 환경에서의 성능 최적화에 중점을 둠.

## 2. 비즈니스 모델 및 기대 효과
* 수익 구조: 중장년층 대상 B2C 판매, 병원 광고 및 진단 중개 수수료 기반 B2B, 치매안심센터 MOU 기반 B2G 등 다각화된 모델 구축.
* 기대 효과: 조기 진단을 통한 건강수명 증진 및 생산인구 확보, 신경계 질환 예방을 통한 사회적 비용 절감 및 ESG 가치 실현.

## 3. 기술 스택

### Language & Framework
<img src="https://img.shields.io/badge/java-007396?style=for-the-badge&logo=java&logoColor=white"> <img src="https://img.shields.io/badge/springboot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"> <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=Spring%20Security&logoColor=white">

### Database & Caching
<img src="https://img.shields.io/badge/mysql-4479A1?style=for-the-badge&logo=mysql&logoColor=white"> <img src="https://img.shields.io/badge/redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">

### Infrastructure & Messaging
<img src="https://img.shields.io/badge/Amazon%20S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white"> <img src="https://img.shields.io/badge/Amazon%20SQS-FF4F8B?style=for-the-badge&logo=amazonsqs&logoColor=white"> <img src="https://img.shields.io/badge/docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">

### External API
<img src="https://img.shields.io/badge/firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=white"> <img src="https://img.shields.io/badge/Kakao%20API-FFCD00?style=for-the-badge&logo=kakao&logoColor=black">

## 4. 시스템 아키텍처

<img width="547" height="257" alt="스크린샷 2026-01-18 171523" src="https://github.com/user-attachments/assets/222863cc-e37f-4625-9268-6ba68b3dbec1" />

## 5. 핵심 기술 결정 및 트러블슈팅

### 5.1. 대규모 데이터 및 성능 최적화

**1) EXPLAIN ANALYZE 분석과 복합 인덱스를 통한 조회 성능 최적화**

* 문제: 사용자별 문진표 이력을 최신순으로 조회하는 쿼리에서 `ORDER BY created_at DESC` 정렬 비용이 발생함.
* 해결: `EXPLAIN ANALYZE`로 실행계획을 확인하고, `survey_results(user_id, created_at DESC)` 복합 인덱스를 적용함.
* 성과: 실행 시간 `9.3ms → 0.17ms`로 약 **54.7배 개선**, 검사 행 수 `189 → 10`으로 약 **18.9배 감소**.

**2) JPA 조회 최적화: Fetch Join, existsByEmail, findAllById 적용**

* 문제: 보호자-환자 조회 시 지연 로딩으로 추가 쿼리가 발생할 수 있었고, 이메일 중복 확인이나 QR 연동 과정에서도 불필요한 엔티티 로딩과 DB 왕복이 존재했음.
* 해결: `existsByEmail`, `LEFT JOIN FETCH`, `findAllById`를 적용해 조회 목적에 맞게 쿼리를 단순화함.
* 성과: 불필요한 엔티티 로딩과 추가 조회를 줄여 DB 접근 횟수와 네트워크 왕복 비용을 줄임.

### 5.2. 분산 환경 대응 및 시스템 회복성(Resilience)

**1) 서버 강제 종료 테스트 후 메시지 큐 기반 비동기 구조로 전환**

* 문제: 기존 `@Async` 기반 인메모리 비동기 구조는 서버 비정상 종료 시 작업 유실 가능성이 있었음.
* 검증: `Runtime.getRuntime().halt(1)` 기반 강제 종료 테스트를 통해, 분석 결과 DB 저장은 완료됐지만 알림은 유실될 수 있음을 확인함.
* 해결: AI 분석 요청과 알림 발송을 메시지 큐 기반 구조로 분리하고, 초기에는 큐 적재 상태를 시각적으로 확인할 수 있는 환경에서 검증한 뒤, AWS 환경 정합성을 위해 최종 구조를 SQS 기반으로 전환함.
* 성과: 메인 요청과 후속 작업을 분리해 작업 유실 가능성을 줄이고, API 서버는 `202 Accepted`로 즉시 응답하는 구조로 개선함.

**2) Redis 기반 QR 토큰 중앙화로 다중 인스턴스 불일치 해결**

* 문제: QR 토큰을 `ConcurrentHashMap`으로 관리해 서버 간 상태 공유가 불가능했음.
* 검증: 8080, 8081 인스턴스 시뮬레이션에서 한 서버가 만든 토큰을 다른 서버가 읽지 못하는 문제를 확인함.
* 해결: `StringRedisTemplate` 기반으로 `QR:LINK:{token}` 저장 구조로 전환하고, TTL 10분과 연동 성공 후 즉시 삭제 로직을 적용함.
* 성과: 다중 인스턴스 환경에서도 동일 토큰을 공유할 수 있도록 개선함.

**3) 병원 조회 로컬 캐시를 글로벌 캐시로 전환**

* 문제: 병원 조회는 유사 좌표 요청이 많아 외부 API 중복 호출 비용이 발생했고, 로컬 캐시(Caffeine)는 서버 간 공유가 불가능했음.
* 해결: Redis 글로벌 캐시로 전환하고 좌표 버킷 기반 키 전략을 적용함. 인증 캐시와 병원 캐시의 성격이 달라 `hospitals` 캐시에 TTL 1일을 별도 적용함.
* 성과: 서버가 달라도 동일 좌표 구역은 캐시를 재사용할 수 있게 했고, 외부 API 중복 호출을 줄임.

**4) 외부 API 장애 전파 차단(Circuit Breaker) 및 비용 최적화**

* 문제: 카카오맵 외부 API 응답 지연 시 메인 서버의 스레드가 묶이고, 무효한 좌표 요청까지 그대로 전달되면 쿼터 낭비가 발생할 수 있었음.
* 해결: `@Valid` 검증으로 잘못된 좌표 요청을 선제적으로 차단하고, `@CircuitBreaker`와 `@Bulkhead`를 적용해 빠른 실패와 동시 호출 제한을 구성함.
* 성과: 외부 API 장애가 전체 서비스 장애로 번지는 문제를 줄이고, 비용 절감과 장애 격리를 함께 고려한 구조를 적용함.

### 5.3. 인증 및 보안 아키텍처 고도화

**1) 데이터 무결성을 위한 Soft Delete 및 인증 아키텍처 리팩토링**

* 문제: 센서 데이터의 참조 무결성 유지를 위해 Soft Delete를 도입하였으나, 서비스 계층의 수동 검증 로직이 Spring Security의 활성화 상태 검증을 우회하여 탈퇴 처리된 유저가 로그인에 성공하는 보안 취약점 발견됨.
* 해결: 기존의 수동 로그인 로직을 제거하고 `AuthenticationManager.authenticate()`로 인증 책임을 위임하여, 프레임워크의 일관된 보안 라이프사이클 안에서 검증되도록 구조를 개선함.
* 성과: 헬스케어 데이터 무결성을 보존함과 동시에 비정상적인 로그인 시도를 인증 관문(Filter)에서 차단함.

**2) Redis 캐싱 도입을 통한 인증 병목 해소 및 정합성 유지**

* 문제: JWT 인증 후 `loadUserByUsername()`로 사용자 정보를 다시 조회하는 구조여서 요청마다 DB 조회가 반복됐고, 인증 구간이 병목이 됨.
* 해결: Redis Look-Aside Cache를 적용하고, 수정/탈퇴 시 Programmatic Eviction으로 stale data를 정리함. 인증 정보는 TTL 60분으로 설정함.
* 성과: 최대 지연 시간 `547ms → 8ms`, 평균 `38ms → 4ms`, 처리량 `8.9/sec → 967.1/sec`로 개선함.

**3) 외부 시스템(AWS S3) 연동 시 DB 트랜잭션 분리 및 보상 처리 적용**

* 문제: S3 업로드 성공 후 DB 저장 실패 시 고아 객체가 남을 수 있었고, 외부 S3 호출이 DB 트랜잭션 안에 포함되면 커넥션 장기 점유로 이어질 수 있었음.
* 해결: S3 업로드와 DB 저장 책임을 분리하고, 메타데이터 저장은 별도 서비스로 분리해 `@Transactional`이 실제로 적용되도록 구조를 변경함. DB 저장 실패 시에는 업로드한 S3 객체를 삭제하는 보상 처리 로직을 추가함.
* 성과: 외부 저장소와 DB를 함께 다루는 업로드 흐름에서 정합성을 유지하는 방향으로 개선함.

**4) 인가(Authorization) 책임 분리 및 권한 매핑 규격 일치화**

* 문제: Service 계층 내부에 인가(권한 검증) 코드가 비즈니스 로직과 혼재되어 유지보수성이 저하됨. 책임을 `SecurityConfig`로 위임하였으나 프레임워크 내부 규격인 `ROLE_` 접두사 누락으로 403 Forbidden 오류가 발생함.
* 해결: Service 내부의 방어 로직을 제거하고 Filter Chain 단계에서 URL 기반으로 권한을 중앙 통제함. JWT 페이로드 및 `UserDetails` 반환부에 프레임워크 요구 규격을 명시적으로 주입함.
* 성과: 비즈니스 로직과 보안 로직의 결합도를 낮추어 코드 가독성 및 유지보수성을 향상시킴.

## 6. API 명세
* [Postman API Documentation](https://documenter.getpostman.com/view/42108335/2sBXcDFgVN)

## 7. 팀원 및 역할
* **김지원 (Backend Lead)**
  * 전체 백엔드 아키텍처 설계 및 인프라 구축
  * 핵심 비즈니스 로직 개발 및 DB 조회 성능 최적화 (복합 인덱스, Fetch Join)
  * Redis, AWS SQS 도입을 통한 대규모 트래픽 분산 처리 및 장애 대응 설계
* **신영서 (Backend & DevOps)**
  * 백엔드 API 공동 개발 및 도메인 로직 구현
  * 클라우드 인프라 운영 환경 구축 및 DevOps 아키텍처 구성
* **하지형 (Frontend / React Native)**
  * React Native 기반 모바일 애플리케이션 UI/UX 개발
  * 백엔드 API 통신 연동 및 클라이언트 상태 관리
* **임은혜 (AI Engineer)**
  * 스마트 인솔 센서(족압, 가속도) 데이터 기반 신경계 질환 위험군 예측 모델 개발 및 학습
* **박수현 (Hardware Engineer)**
  * 스마트 인솔 디바이스 설계 및 센서 데이터 수집/전송 모듈 하드웨어 제작
