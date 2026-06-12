# CatchMe - 스마트 인솔 기반 보행 데이터 분석 및 신경계 질환 예측 플랫폼 API 서버

> 스마트 인솔에서 수집한 보행 데이터와 문진 정보를 기반으로 뇌질환 위험 신호를 확인하고, 보호자 알림과 병원 안내까지 연결하는 헬스케어 플랫폼 백엔드 API 서버

<br>

## 1. 프로젝트 소개

CatchMe는 스마트 인솔에서 수집한 족압·가속도 기반 보행 데이터와 사용자 문진 정보를 활용해 뇌질환 위험 신호를 조기에 확인하는 헬스케어 플랫폼임.

백엔드 서버는 사용자 인증, 보호자-환자 연동, Raw 데이터 업로드, 분석 요청, 결과 조회, 보호자 알림, 병원 조회 API를 담당함.

<br>

## 2. 담당 역할

**Spring Boot 기반 백엔드 개발 및 API 설계 담당**

* 보호자-환자 연동, Raw 데이터 업로드, 분석 요청, 결과 조회, 보호자 알림 기능 구현
* MySQL 기반 도메인 모델 설계 및 조회 성능 개선
* Redis 기반 인증 캐시, QR 토큰 중앙화, 병원 조회 글로벌 캐시 및 장애 대응 적용
* AWS S3 기반 Raw 데이터 저장 및 상태 기반 복구 구조 개선
* AWS SQS 기반 비동기 처리 구조 도입
* ShedLock, Circuit Breaker, Bulkhead를 활용한 다중 인스턴스 및 외부 장애 대응 구조 개선

<br>

## 3. 핵심 성과 요약

| 구분 | 개선 내용 |
|---|---|
| DB 조회 성능 개선 | 복합 인덱스 적용으로 문진표 이력 조회 실행 시간 `9.3ms → 0.17ms` |
| 인증 병목 완화 | memberId 기반 Redis 인증 캐시 적용으로 평균 응답 시간 `6.365ms → 3.068ms`, p95 `7.486ms → 3.906ms` |
| Redis 장애 대응 | 인증은 DB 조회, 병원 조회는 Kakao API 호출로 fallback하고 QR 기능은 503으로 명확히 중단 |
| 다중 인스턴스 대응 | QR 토큰 저장소를 Redis로 중앙화하여 서버 간 상태 불일치 해결 |
| 외부 API 비용 절감 | 병원 조회 로컬 캐시를 Redis 글로벌 캐시로 전환 |
| 비동기 안정성 개선 | `@Async` 기반 인메모리 구조를 AWS SQS 메시지 큐 구조로 전환 |
| 데이터 정합성 개선 | S3 업로드 상태 기반 복구 구조와 ShedLock 적용 |

<br>

## 4. 주요 기능

* 사용자 회원가입, 로그인, JWT 인증
* 보호자-환자 QR 연동
* 스마트 인솔 Raw 데이터 업로드
* 분석 요청 및 결과 조회
* 위험군 결과 기반 보호자 알림
* 사용자별 문진표 이력 조회
* 사용자 위치 기반 병원 조회
* Redis 장애 시 기능별 fallback 및 QR 503 처리
* 외부 API 장애 시 fallback 처리

<br>

## 5. 기술 스택

### Language & Framework

<img src="https://img.shields.io/badge/java-007396?style=for-the-badge&logo=java&logoColor=white"> <img src="https://img.shields.io/badge/springboot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"> <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=Spring%20Security&logoColor=white">

### Database & Caching

<img src="https://img.shields.io/badge/mysql-4479A1?style=for-the-badge&logo=mysql&logoColor=white"> <img src="https://img.shields.io/badge/redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">

### Infrastructure & Messaging

<img src="https://img.shields.io/badge/Amazon%20S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white"> <img src="https://img.shields.io/badge/Amazon%20SQS-FF4F8B?style=for-the-badge&logo=amazonsqs&logoColor=white"> <img src="https://img.shields.io/badge/docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">

### External API

<img src="https://img.shields.io/badge/firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=white"> <img src="https://img.shields.io/badge/Kakao%20API-FFCD00?style=for-the-badge&logo=kakao&logoColor=black">

<br>

## 6. 시스템 아키텍처

<img width="547" height="257" alt="CatchMe 시스템 아키텍처" src="https://github.com/user-attachments/assets/222863cc-e37f-4625-9268-6ba68b3dbec1" />

<br>

## 7. 핵심 기술 결정 및 트러블슈팅

운영 중 발생할 수 있는 성능 병목, 다중 인스턴스 환경의 상태 불일치, 외부 시스템 연동 실패, 비동기 작업 유실 가능성을 직접 재현하고 구조를 개선함.


<br>

### 7.1. 성능 병목 개선

| 개선 항목 | 문제 | 해결 | 결과 |
|---|---|---|---|
| 문진표 이력 조회 최적화 | 최신순 조회 시 정렬 비용 발생 | `user_id, created_at DESC` 복합 인덱스 적용 | 실행 시간 `9.3ms → 0.17ms`, 약 **54.7배 개선** |
| 인증 구간 DB 조회 감소 | JWT 인증 시 최신 회원 상태 확인을 위한 DB 조회 반복 | memberId 기반 Redis Cache-Aside와 최소 인증 DTO 적용 | 평균 `6.365ms → 3.068ms`, p95 `7.486ms → 3.906ms` |

#### 1) 실행계획 분석 기반 문진표 이력 조회 최적화

사용자별 문진표 이력을 최신순으로 조회하는 쿼리는 `WHERE user_id = ? ORDER BY created_at DESC LIMIT 10` 구조였음.
`EXPLAIN ANALYZE` 확인 결과, 필요한 결과는 10건이지만 189개 행을 확인한 뒤 정렬하는 비용이 발생하고 있었음.

이를 해결하기 위해 조회 조건과 정렬 조건을 함께 반영한 복합 인덱스를 적용함.

```sql
CREATE INDEX idx_survey_user_created
ON survey_results (user_id, created_at DESC);
```

그 결과 실행 시간은 `9.3ms → 0.17ms`로 약 **54.7배 개선**되었고, 검사 행 수는 `189개 → 10개`로 감소함.

#### 2) Redis 인증 캐시 도입

JWT 내부 권한을 그대로 신뢰하지 않고, 토큰의 `id` claim으로 회원을 식별한 뒤 서버의 최신 탈퇴 여부와 Role을 확인하도록 구성함.
다만 캐시가 없으면 모든 보호 API 요청에서 `MemberRepository.findById()`가 반복되는 문제가 있었음.

이를 해결하기 위해 인증 상태 조회를 `MemberAuthLookupService`로 분리하고 `@Cacheable` 기반 Redis Cache-Aside를 적용함.

```text
JWT id claim
→ memberId 기반 인증 캐시 조회
→ Cache Miss 시 DB 조회 및 DTO 저장
→ MemberPrincipal 생성
→ SecurityContext 저장
```

캐시 Key는 변경 가능한 이메일 대신 `memberId`를 사용했으며, Redis에는 JPA 엔티티가 아닌 `memberId`, `email`, `role`, `enabled`만 가진 `MemberAuthCacheDto`를 저장함.
회원 탈퇴와 비밀번호 변경 시에는 DB 트랜잭션 커밋 이후 해당 회원의 캐시를 삭제하고, 인증 캐시 TTL은 30분으로 설정함.

성능 검증은 비즈니스 로직이 없는 보호 API를 대상으로 k6를 사용해 진행함.
캐시 미적용 Profile과 Redis 캐시 적용 Profile에 동일하게 100 RPS를 2분간 3회씩 전달하고, 각 지표의 중앙값을 비교함.

| 지표 | 캐시 미적용 | Redis 캐시 적용 | 개선 |
|---|---:|---:|---:|
| 평균 응답 시간 | 6.365ms | 3.068ms | **51.8% 감소** |
| p95 | 7.486ms | 3.906ms | **47.8% 감소** |
| p99 | 8.187ms | 4.346ms | **46.9% 감소** |
| 오류율 / 요청 누락 | 0% / 0건 | 0% / 0건 | 안정성 유지 |

이를 통해 서버의 최신 회원 상태를 확인하는 인증 구조를 유지하면서도, 반복적인 DB 조회 비용을 Redis 인증 캐시로 줄일 수 있음을 확인함.

<br>

### 7.2. 다중 인스턴스 환경 대응

| 개선 항목       | 문제                                           | 해결                                       | 결과                        |
| ----------- | -------------------------------------------- | ---------------------------------------- | ------------------------- |
| QR 토큰 중앙화   | `ConcurrentHashMap` 기반 토큰 저장으로 서버 간 상태 공유 불가 | Redis `StringRedisTemplate` 기반 중앙 저장소 구성 | 서로 다른 인스턴스에서도 동일 토큰 검증 가능 |
| 병원 조회 캐시 공유 | 로컬 캐시 사용으로 인스턴스별 외부 API 중복 호출 발생             | Redis 글로벌 캐시와 좌표 버킷 전략 적용                | 동일 좌표 구역의 외부 API 중복 호출 감소 |

#### 1) Redis 기반 QR 토큰 중앙화

보호자-환자 연동에 사용하는 QR 토큰을 기존에는 `ConcurrentHashMap`에 저장하고 있었음.
단일 서버에서는 문제가 없었지만, 8080과 8081 두 인스턴스를 실행해 확인한 결과 한 서버에서 생성한 토큰을 다른 서버에서 조회하지 못하는 문제가 발생함.

이를 해결하기 위해 QR 토큰 저장소를 Redis로 전환함.

* Key: `QR:LINK:{token}`
* Value: 사용자 ID
* TTL: 10분
* 연동 성공 후 즉시 삭제

그 결과 다중 인스턴스 환경에서도 동일한 QR 토큰을 공유하고 검증할 수 있는 구조로 개선함.

#### 2) 병원 조회 캐시를 Redis 글로벌 캐시로 전환

병원 조회 기능은 사용자 위치를 기준으로 카카오 API를 호출하는 구조였음.
초기에는 로컬 캐시를 사용했지만, 서버가 여러 대일 경우 동일한 좌표 요청도 인스턴스별로 다시 외부 API를 호출하는 문제가 있었음.

이를 해결하기 위해 병원 조회 캐시를 Redis 글로벌 캐시로 전환하고, 유사 좌표는 같은 캐시 키를 사용하도록 좌표 버킷 전략을 적용함.
또한 인증 캐시와 병원 조회 캐시의 성격이 다르다고 판단해 병원 캐시는 TTL 1일로 분리 적용함.

그 결과 인스턴스가 달라도 동일 좌표 구역의 캐시를 재사용할 수 있게 되었고, 외부 API 중복 호출을 줄임.

#### 3) Redis 장애 시 기능별 대응

Redis 데이터를 모두 같은 방식으로 처리하지 않고, 데이터의 재생성 가능성과 역할에 따라 장애 정책을 분리함.

| 기능 | Redis 장애 시 처리 | 이유 |
|---|---|---|
| 인증 캐시 | MySQL에서 최신 회원 상태와 Role 조회 | DB에 원본 데이터 존재 |
| 병원 조회 캐시 | Kakao API 직접 호출 후 결과 반환 | 외부 API에서 재조회 가능 |
| QR 토큰 | `503 Service Unavailable` 반환 | Redis에만 존재하는 일회성 상태 데이터 |

Spring Cache GET·PUT 오류는 `CacheErrorHandler`에서 예외를 전파하지 않아 원본 메서드가 실행되도록 구성함.
QR 토큰은 `StringRedisTemplate`을 직접 사용하므로 Redis 예외를 전용 예외로 변환해 503을 반환하고, 연결 후 토큰 삭제 실패 시 DB 트랜잭션을 롤백해 부분 성공을 방지함.

<br>

### 7.3. 비동기 처리와 장애 대응 구조 개선

| 개선 항목         | 문제                                 | 해결                                     | 결과                            |
| ------------- | ---------------------------------- | -------------------------------------- | ----------------------------- |
| 위험군 알림 비동기 처리 | `@Async` 기반 인메모리 작업은 서버 종료 시 유실 가능 | AWS SQS 기반 메시지 큐 구조로 전환                | 작업을 서버 메모리 밖에 보관해 유실 가능성 완화   |
| 외부 API 장애 격리  | 카카오 API 지연 시 요청 스레드 점유             | Circuit Breaker, Bulkhead, Fallback 적용 | 외부 장애가 전체 서비스 지연으로 전파되는 위험 완화 |

#### 1) SQS 기반 비동기 처리 구조 도입

기존 알림 발송 로직은 `@Async`와 `ThreadPoolTaskExecutor` 기반의 인메모리 비동기 구조였음.
정상 상황에서는 요청 처리와 알림 발송을 분리할 수 있었지만, 서버가 비정상 종료되면 대기 중인 알림 작업이 유실될 수 있었음.

이를 검증하기 위해 `Runtime.getRuntime().halt(1)`로 서버 강제 종료 상황을 시뮬레이션했고, 분석 결과는 DB에 저장되었지만 보호자 알림은 발송되지 않는 상태 불일치 가능성을 확인함.

이후 분석 요청과 알림 발송을 메시지 큐 기반으로 분리하고, 최종적으로 AWS SQS 기반 비동기 처리 구조로 전환함.
API는 요청 수신 후 `202 Accepted`를 즉시 반환하고, 실제 후속 작업은 Consumer가 처리하도록 구성함.

이를 통해 요청 처리 스레드 점유를 줄이고, 후속 작업을 서버 메모리 밖에 남길 수 있는 구조로 개선함.

#### 2) 외부 API 장애 격리

병원 조회 기능은 카카오 API에 직접 의존하고 있어, 외부 API 지연이나 장애가 발생하면 요청 스레드가 타임아웃까지 대기할 수 있었음.
동시 요청이 몰릴 경우 외부 API 장애가 서비스 전체 지연으로 번질 수 있다고 판단함.

이를 해결하기 위해 Resilience4j 기반의 `@CircuitBreaker`와 `@Bulkhead`를 적용함.

* `@CircuitBreaker`: 실패율 증가 시 외부 API 호출 차단
* `@Bulkhead`: 외부 API 동시 호출 수 제한
* Fallback: 장애 시 빈 병원 목록 반환

그 결과 외부 API 장애 상황에서도 빠르게 우회할 수 있는 구조를 구성함.

<br>

### 7.4. Raw 데이터 업로드 정합성 및 자동 복구 구조 개선

스마트 인솔에서 수집된 보행 센서 데이터는 분석의 원본 데이터이므로, CSV 파일로 변환해 S3에 저장하고 DB에는 파일 경로와 메타데이터를 저장하는 구조였음.

기존에는 S3 업로드 성공 후 DB 저장이 실패하면 고아 객체를 막기 위해 S3 파일을 삭제했음.
하지만 헬스케어 Raw 데이터는 추후 분석과 장애 복구에 필요한 원본 데이터이기 때문에, DB 저장 실패만으로 파일을 삭제하는 방식은 적절하지 않다고 판단함.

이를 해결하기 위해 업로드 작업 상태를 별도 테이블로 관리하는 상태 기반 복구 구조로 개선함.

| 실패 유형                | 처리 방식                                                          |
| -------------------- | -------------------------------------------------------------- |
| S3 업로드 실패            | 요청 중 최대 3회 즉시 재시도 후, 실패 시 클라이언트 재전송 대상으로 분리                    |
| S3 업로드 성공 + DB 저장 실패 | S3 파일을 삭제하지 않고 `DB_SAVE_FAILED` 상태로 기록 후 스케줄러가 DB 메타데이터 저장 재시도 |
| 복구 반복 실패             | `RECOVERY_FAILED` 상태와 재시도 횟수 기록                                |
| 다중 인스턴스 스케줄러 중복 실행   | ShedLock을 적용해 하나의 인스턴스만 복구 작업 수행                               |

업로드 상태는 `PENDING`, `S3_UPLOADED`, `DB_SAVE_FAILED`, `S3_UPLOAD_FAILED`, `COMPLETED`, `RECOVERY_FAILED` 등으로 관리함.

이를 통해 단순 고아 객체 삭제 방식에서 벗어나, 원본 건강 데이터 보존과 장애 복구 가능성을 함께 고려한 업로드 구조로 개선함.

<br>

### 7.5. JPA 조회 로직 세부 최적화

반복 조회가 발생할 수 있는 구간에서는 조회 목적에 맞게 쿼리를 단순화함.

* 회원가입 이메일 중복 확인: `findByEmail` → `existsByEmail`

  * 엔티티 전체 로딩 없이 존재 여부만 확인
* 보호자-환자 연관 정보 조회: `LEFT JOIN FETCH` 적용

  * 연관 엔티티 접근 시 발생할 수 있는 추가 조회 감소
* QR 연동 사용자 조회: 개별 `findById` 2회 → `findAllById` 1회

  * DB 왕복 횟수와 트랜잭션 내 커넥션 점유 시간 감소

<br>

## 8. API 명세

* [Postman API Documentation](https://documenter.getpostman.com/view/42108335/2sBXcDFgVN)

<br>

## 9. 팀원 및 역할

| 이름      | 역할                      | 담당 내용                                                                                                          |
| ------- | ----------------------- | -------------------------------------------------------------------------------------------------------------- |
| **김지원** | Backend                 | Spring Boot 기반 API 설계 및 구현, 보호자-환자 연동, Raw 데이터 업로드, 분석 요청, 결과 조회, 알림 흐름 구현, DB 성능 개선, Redis/SQS/S3 기반 운영 구조 개선 |
| **신영서** | Backend & DevOps        | 백엔드 API 공동 개발, 도메인 로직 구현, 클라우드 인프라 운영 환경 구축                                                                    |
| **하지형** | Frontend / React Native | React Native 기반 모바일 애플리케이션 UI/UX 개발, 백엔드 API 통신 연동                                                             |
| **임은혜** | AI Engineer             | 스마트 인솔 센서 데이터 기반 신경계 질환 위험군 예측 모델 개발 및 학습                                                                      |
| **박수현** | Hardware Engineer       | 스마트 인솔 디바이스 설계 및 센서 데이터 수집/전송 모듈 제작                                                                            |
