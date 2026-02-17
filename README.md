# CatchMe - 스마트 인솔 기반 보행 데이터 분석 및 신경계 질환 예측 플랫폼 API 서버

## 1. 프로젝트 개요
스마트 깔창에서 수집된 족압 및 가속도 데이터를 기반으로 환자의 보행 패턴을 분석하고, 이상 징후 감지 시 보호자에게 실시간 알림을 제공하는 헬스케어 백엔드 시스템. 환자의 건강과 직결될 수 있는 데이터를 다루는 만큼, **데이터 무결성, 고가용성(High Availability), 그리고 대규모 트래픽 환경에서의 성능 최적화**에 중점을 두고 아키텍처를 설계함.

## 2. 기술 스택
* **Language & Framework**: Java 17, Spring Boot, Spring Security, Spring Data JPA
* **Database & Caching**: MySQL, Redis (Caffeine Cache 혼용)
* **Infrastructure & Messaging**: AWS S3, AWS SQS (LocalStack), Docker, Docker-compose
* **External API**: Firebase Cloud Messaging (FCM), Kakao Local API

## 3. 시스템 아키텍처

<img width="547" height="257" alt="스크린샷 2026-01-18 171523" src="https://github.com/user-attachments/assets/222863cc-e37f-4625-9268-6ba68b3dbec1" />

---

## 4. 핵심 기술 결정 및 트러블슈팅

### 4.1. 대규모 데이터 및 성능 최적화
**1) EXPLAIN ANALYZE 분석과 복합 인덱스를 통한 조회 성능 54배 개선**
* **문제**: 특정 유저의 최근 데이터를 페이징 조회할 때, 인덱스 부재로 인해 DB가 전체 결과를 메모리에 올린 후 정렬하는 작업(Table Scan Sort)이 발생하여 성능 저하 우려.
* **해결**: 조회 조건(`user_id`)과 정렬 조건(`created_at DESC`)을 결합한 복합 인덱스(Composite Index)를 설계하여 적용.
* **성과**: 정렬 연산 단계가 0회로 제거되었으며, 스캔 행(Rows Examined)을 189개에서 10개로 축소시켜 응답 속도를 9.3ms에서 0.17ms로 최적화.



**2) JPA Proxy 이해를 바탕으로 한 Fetch Join 및 Network I/O 최적화**
* **문제**: 보호자와 환자의 1:1 매핑 데이터 탐색 시, JPA 지연 로딩(Lazy Loading)으로 인한 1+1, N+1 형태의 추가 SELECT 쿼리 발생.
* **해결**: 특정 비즈니스 로직에 한해 JPQL의 `LEFT JOIN FETCH`를 사용하여 명시적으로 연관 엔티티를 영속성 컨텍스트에 적재. 단건 외 다건 조회 시에는 `findAllById` 후 Map 매핑 로직 적용.
* **성과**: 객체 탐색 시 발생하던 지연 쿼리를 제거하여 DB 접근 횟수를 50% 절감.

### 4.2. 분산 환경 대응 및 시스템 회복성(Resilience)
**1) Hard Crash 시뮬레이션을 통한 데이터 유실 증명 및 SQS 기반 비동기 아키텍처 도입**
* **문제**: 기존 `@Async`(인메모리 큐) 기반의 알림 처리는 서버 강제 종료(SIGKILL) 시 크리티컬 알림 데이터가 영구 증발하는 결함 존재. AI 분석으로 인한 톰캣 스레드 고갈 위험 확인.
* **해결**: 시스템 결합도를 낮추고 작업의 영속성을 보장하기 위해 AWS SQS를 브로커로 도입. API 서버는 큐에 이벤트 DTO만 전달 후 즉시 스레드를 반환(HTTP 202 Accepted)하고, 백그라운드 컨슈머가 AI 분석과 FCM 처리를 담당하도록 분리.
* **성과**: 서버 예기치 않은 종료 시에도 SQS에 보관된 메시지가 보존되어 Zero Data Loss 달성 및 메인 서버의 스레드 고갈 방지.



**2) Scale-out 환경의 상태 불일치 해결 및 글로벌 캐시(Redis) 전환**
* **문제**: 서버 스케일 아웃(Scale-out) 시, 로컬 캐시에 저장된 QR 토큰을 다른 서버가 인식하지 못하는 세션 불일치 발생. 외부 API 로컬 캐싱 시 Cache Miss 중복 발생.
* **해결**: `StringRedisTemplate`을 이용해 토큰과 상태를 중앙 집중화하고 TTL 자동화 적용. 외부 저장소 사용 시 발생하는 JSON 역직렬화 에러를 DTO 기본 생성자 추가로 해결.
* **성과**: 다중 서버 환경에서도 완벽한 데이터 정합성을 유지하며, 외부 API 결과에 대한 글로벌 캐싱 적용으로 과금을 효과적으로 방어.

**3) 외부 API 연동 시 장애 전파 차단(Circuit Breaker) 및 비용 최적화**
* **문제**: 카카오맵 외부 API 응답 지연 시 메인 서버의 스레드 대기로 인한 연쇄 장애 위험 및 무효한 좌표 요청에 의한 쿼터 낭비 존재.
* **해결**: `Resilience4j`의 `@CircuitBreaker` 및 `@Bulkhead`를 도입해 임계치 초과 시 빠른 실패(Fail-Fast)와 Graceful Degradation 유도. 컨트롤러 단 검증(`@Valid`, `@Min/Max`) 강화.
* **성과**: 외부 인프라 장애 시에도 메인 시스템 안정성을 확보하고, 불필요한 API 호출을 사전에 차단.

### 4.3. 인증 및 보안 아키텍처 고도화
**1) 데이터 무결성을 위한 Soft Delete 도입 및 Spring Security 인증 아키텍처 리팩토링**
* **문제**: 센서 데이터의 참조 무결성을 위해 Soft Delete를 도입했으나, 서비스 계층의 수동 검증 로직이 Spring Security의 활성화 상태 검증을 우회하여 탈퇴 유저가 로그인되는 취약점 발견.
* **해결**: 수동 로그인 로직을 제거하고 `AuthenticationManager.authenticate()`로 인증을 위임하여 프레임워크의 일관된 보안 체계 안에서 검증되도록 구조 개선.
* **성과**: 헬스케어 데이터 무결성을 보존하면서도 비정상 로그인 시도를 인증 관문에서 원천 차단.

**2) Redis 캐싱 도입을 통한 인증 병목 해소 및 Cache Eviction 정합성 유지**
* **문제**: 매 요청마다 JWT 필터가 동작하며 사용자 상태를 DB에서 조회하여 커넥션 풀 경합 발생(Max Latency 547ms). 캐싱 이후엔 데이터 수정 시 과거 데이터가 남는 Stale Data 문제 발생.
* **해결**: Redis Look-Aside 캐싱 전략을 적용해 인증 데이터를 인메모리에서 즉시 반환 처리. 정보 수정 트랜잭션 완료 직후 Programmatic Eviction(수동 강제 삭제)을 적용하여 동기화.
* **성과**: 응답 지연을 평균 38ms에서 4ms로 98.5% 단축하고, 처리량(Throughput)을 108배 향상시키며 정합성 보장.

**3) 외부 시스템(AWS S3) 연동 시 DB 트랜잭션 분리 및 보상 트랜잭션 적용**
* **문제**: S3 업로드와 DB 저장이 단일 `@Transactional`로 묶여 있어 외부 네트워크 지연이 DB 커넥션 점유로 이어짐. 내부 호출 문제로 롤백 실패 시 고아 객체 발생.
* **해결**: S3 네트워크 I/O를 트랜잭션 외부로 분리하고, DB 저장은 별도 Service로 추출해 프록시를 타게 함. DB 트랜잭션 실패 시 S3 객체를 삭제하는 보상 트랜잭션 적용.
* **성과**: 분산 환경에서의 데이터 정합성을 보장하고 커넥션 풀 고갈 위험 제거.

**4) 인가(Authorization) 책임 분리 및 Spring Security 권한 매핑 규격 일치화**
* **문제**: Service 계층 내부에 권한 검증 코드가 혼재되어 가독성 저하. 책임을 `SecurityConfig`로 위임했으나 프레임워크 내부 규격(`ROLE_` 접두사) 누락으로 403 오류 발생.
* **해결**: Service 내부 방어 로직을 전면 제거하고 Filter Chain 단에서 권한을 중앙 통제. JWT 페이로드 및 `UserDetails` 반환부에 프레임워크 요구 규격을 명시적 주입.
* **성과**: 비즈니스와 보안/인가 로직의 결합도를 낮추고 유지보수성 향상.

---

## 5. API 명세
* [Postman API Documentation](https://documenter.getpostman.com/view/42108335/2sBXcDFgVN)

## 6. 팀원 및 역할

* **김지원 (Backend Lead)** * 전체 백엔드 아키텍처 설계 및 인프라 구축
  * 핵심 비즈니스 로직 개발 및 DB 조회 성능 최적화 (복합 인덱스, Fetch Join)
  * Redis, AWS SQS 도입을 통한 대규모 트래픽 분산 처리 및 장애 대응 설계
* **신영서 (Backend & DevOps)** * 백엔드 API 공동 개발 및 도메인 로직 구현
  * 클라우드 인프라 운영 환경 구축 및 DevOps 아키텍처 구성
* **하지형 (Frontend / React Native)**
  * React Native 기반 모바일 애플리케이션 UI/UX 개발
  * 백엔드 API 통신 연동 및 클라이언트 상태 관리
* **임은혜 (AI Engineer)**
  * 스마트 인솔 센서(족압, 가속도) 데이터 기반 신경계 질환 위험군 예측 모델 개발 및 학습
* **박수현 (Hardware Engineer)**
  * 스마트 인솔 디바이스 설계 및 센서 데이터 수집/전송 모듈 하드웨어 제작
