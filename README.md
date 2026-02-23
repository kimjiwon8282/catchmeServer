# CatchMe - 스마트 인솔 기반 보행 데이터 분석 및 신경계 질환 예측 플랫폼 API 서버

## 1. 프로젝트 개요 및 배경

### 1.1. 프로젝트 목적
스마트 인솔(Smart Insole)에서 수집된 족압 및 가속도 데이터를 기반으로 환자의 보행 패턴을 분석하고, 치매 및 뇌졸중, 파킨슨병 등 신경계 질환의 이상 징후를 조기 예측하여 보호자에게 실시간 알림을 제공하는 헬스케어 백엔드 시스템 구축임.

### 1.2. 추진 배경 및 문제 인식
* 치매 환자 및 사회적 비용 급증: 국내 65세 이상 치매 환자 수는 2015년 약 63만 명에서 2023년 약 98만 명으로 57% 증가하였으며, 국가 치매 관리 비용은 2021년 기준 18.7조 원에 육박함.
* 조기 진단의 한계: 초기 증상을 단순 노화로 오인하거나, 인지검사 비용 부담 및 부정적 인식으로 인해 병원 방문을 꺼리는 심리적 장벽이 존재함.

### 1.3. 솔루션 및 차별점
* 하드웨어 및 데이터 수집: 피에조 센서(발바닥 압력)와 가속도 센서(보폭 및 움직임)를 장착한 스마트 인솔을 통해 블루투스 5.0 기반으로 안정적인 보행 데이터를 수집함.
* 소프트웨어 및 예측: CNN, EfficientNetV2 모델을 활용한 AI 기반 보행 습관 분석을 통해 뇌질환 가능성을 예측함.
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
**1) EXPLAIN ANALYZE 분석과 복합 인덱스를 통한 조회 성능 54배 개선**
* 문제: 특정 유저의 최근 데이터를 페이징 조회할 때 인덱스 부재로 인해 DB가 전체 결과를 메모리에 올린 후 정렬하는 Table Scan Sort 작업이 발생하여 심각한 성능 저하 우려됨.
* 해결: 조회 조건(`user_id`)과 정렬 조건(`created_at DESC`)을 결합한 복합 인덱스(Composite Index)를 설계하여 쿼리 실행 계획에 적용함.
* 성과: 정렬 연산 단계가 0회로 제거됨. 스캔 행(Rows Examined)을 189개에서 10개로 축소시켜 응답 속도를 9.3ms에서 0.17ms로 최적화함.

**2) JPA Proxy 이해를 바탕으로 한 Fetch Join 및 Network I/O 최적화**
* 문제: 보호자와 환자의 1:1 매핑 데이터 탐색 시 JPA 지연 로딩(Lazy Loading) 정책으로 인해 1+1, N+1 형태의 추가 SELECT 쿼리가 대량 발생함.
* 해결: 특정 비즈니스 로직에 한하여 JPQL의 `LEFT JOIN FETCH`를 사용하여 명시적으로 연관 엔티티를 영속성 컨텍스트에 한 번에 적재함. 단건 외 다건 조회 시에는 `findAllById` 호출 후 애플리케이션 메모리 단에서 Map 매핑 로직을 적용함.
* 성과: 객체 그래프 탐색 시 발생하던 불필요한 지연 쿼리를 완벽히 제거하여 DB 접근(Network I/O) 횟수를 50% 절감함.

### 5.2. 분산 환경 대응 및 시스템 회복성(Resilience)
**1) Hard Crash 시뮬레이션 및 SQS 기반 비동기 아키텍처 도입**
* 문제: 기존 `@Async` 기반 인메모리 큐를 활용한 알림 처리는 서버 강제 종료(SIGKILL) 시 크리티컬 알림 데이터가 영구 증발하는 결함이 존재함. 또한, 무거운 AI 분석 요청 대기로 인해 톰캣 스레드 풀 고갈 위험이 확인됨.
* 해결: 시스템 결합도를 낮추고 작업 영속성을 보장하기 위해 AWS SQS(LocalStack 연동)를 메시지 브로커로 도입함. API 서버는 큐에 이벤트 DTO만 전달 후 즉시 스레드를 반환(HTTP 202 Accepted)하고, 별도의 백그라운드 컨슈머가 AI 분석 및 FCM 통신을 전담하도록 아키텍처를 분리함.
* 성과: 예기치 않은 서버 종료 시에도 SQS의 가시성 시간(Visibility Timeout) 및 ACK 메커니즘을 통해 메시지가 보존되어 Zero Data Loss를 달성함. API 서버의 스레드 고갈 문제를 원천 차단함.

**2) Scale-out 환경의 상태 불일치 해결 및 글로벌 캐시(Redis) 전환**
* 문제: 트래픽 증가로 인한 서버 스케일 아웃(Scale-out) 시, 로컬 인메모리 캐시에 저장된 QR 토큰을 다른 잉여 서버가 인식하지 못하는 세션 불일치 오류가 발생함. 외부 API 호출 결과의 로컬 캐싱 시 Cache Miss 중복으로 인한 비용 낭비 확인됨.
* 해결: `StringRedisTemplate`을 이용해 토큰과 상태 데이터를 Redis로 중앙 집중화하고 TTL 자동화 정책을 적용함. 외부 저장소 사용 시 발생하는 JSON 역직렬화 에러는 DTO 기본 생성자 추가로 해결함.
* 성과: 다중 서버 환경에서도 완벽한 데이터 정합성을 유지함. 외부 API 결과에 대한 글로벌 캐싱 적용으로 불필요한 네트워크 호출을 막아 과금을 효과적으로 방어함.

**3) 외부 API 연동 시 장애 전파 차단(Circuit Breaker) 및 비용 최적화**
* 문제: 카카오맵 외부 API 응답 지연 시 메인 서버의 스레드가 무한 대기 상태에 빠져 연쇄 장애로 이어질 위험 및 무효한 좌표 요청에 의한 일일 쿼터 낭비 발생함.
* 해결: `Resilience4j` 라이브러리의 `@CircuitBreaker` 및 `@Bulkhead` 패턴을 도입해 임계치 초과 시 빠른 실패(Fail-Fast)와 Graceful Degradation을 유도함. 컨트롤러 단 검증(`@Valid`, `@Min/Max`)을 선제적으로 강화함.
* 성과: 외부 인프라 장애 발생 시에도 메인 시스템의 독립적인 생존성 및 안정성을 확보하고, 불필요한 API 호출을 사전에 차단함.

### 5.3. 인증 및 보안 아키텍처 고도화
**1) 데이터 무결성을 위한 Soft Delete 및 인증 아키텍처 리팩토링**
* 문제: 센서 데이터의 참조 무결성 유지를 위해 Soft Delete를 도입하였으나, 서비스 계층의 수동 검증 로직이 Spring Security의 활성화 상태 검증을 우회하여 탈퇴 처리된 유저가 로그인에 성공하는 보안 취약점 발견됨.
* 해결: 기존의 수동 로그인 로직을 전면 제거하고 `AuthenticationManager.authenticate()`로 인증 책임을 위임하여, 프레임워크의 일관된 보안 라이프사이클 안에서 검증되도록 구조를 개선함.
* 성과: 헬스케어 데이터 무결성을 보존함과 동시에 비정상적인 로그인 시도를 인증 관문(Filter)에서 원천 차단함.

**2) Redis 캐싱 도입을 통한 인증 병목 해소 및 정합성 유지**
* 문제: 매 API 요청마다 동작하는 JWT 필터가 사용자 상태 검증을 위해 DB를 조회하여 커넥션 풀 경합이 발생함(Max Latency 547ms). 캐시 도입 후 정보 수정 시 과거 데이터가 남아있는 Stale Data 문제가 발생함.
* 해결: Redis Look-Aside 캐싱 전략을 적용하여 인증 데이터를 인메모리에서 즉시 반환하도록 처리함. 사용자 정보 수정 트랜잭션이 완료된 직후 Programmatic Eviction(수동 강제 삭제) 로직을 추가하여 캐시 동기화를 수행함.
* 성과: 인증 응답 지연을 평균 38ms에서 4ms로 98.5% 단축하였으며, 시스템 전체 처리량(Throughput)을 108배 향상시킴과 동시에 데이터 정합성을 완벽히 보장함.

**3) 외부 시스템(AWS S3) 연동 시 DB 트랜잭션 분리 및 보상 트랜잭션 적용**
* 문제: S3 파일 업로드와 DB 메타데이터 저장이 단일 `@Transactional`로 강하게 묶여 있어, 외부 네트워크 지연이 DB 커넥션 장기 점유로 이어짐. 내부 메서 호출 문제로 프록시가 작동하지 않아 예외 발생 시 S3 고아 객체가 남는 문제 발생함.
* 해결: S3 네트워크 I/O 로직을 DB 트랜잭션 외부로 완전히 분리하고, DB 저장은 별도 Service 클래스로 추출하여 트랜잭션 프록시를 정상적으로 타게 구조화함. DB 저장 실패 시 이미 업로드된 S3 객체를 삭제하는 보상 트랜잭션(Compensating Transaction) 패턴을 적용함.
* 성과: 분산 환경에서의 데이터 정합성을 보장하고 커넥션 풀 고갈 위험을 완벽히 제거함.

**4) 인가(Authorization) 책임 분리 및 권한 매핑 규격 일치화**
* 문제: Service 계층 내부에 인가(권한 검증) 코드가 비즈니스 로직과 혼재되어 유지보수성이 저하됨. 책임을 `SecurityConfig`로 위임하였으나 프레임워크 내부 규격인 `ROLE_` 접두사 누락으로 403 Forbidden 오류가 발생함.
* 해결: Service 내부의 방어 로직을 전면 제거하고 Filter Chain 단계에서 URL 기반으로 권한을 중앙 통제함. JWT 페이로드 및 `UserDetails` 반환부에 프레임워크 요구 규격을 명시적으로 주입함.
* 성과: 비즈니스 로직과 보안 로직의 결합도를 낮추어 코드 가독성 및 유지보수성을 크게 향상시킴.

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
