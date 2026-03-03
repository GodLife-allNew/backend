# GodLife - 갓생 루틴 관리 플랫폼 백엔드

> 목표를 향해 꾸준히 나아가는 사람들을 위한 루틴 관리 및 챌린지 플랫폼

---

## 목차

- [프로젝트 소개](#프로젝트-소개)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [주요 기능](#주요-기능)
- [프로젝트 구조](#프로젝트-구조)
- [환경 설정](#환경-설정)
- [실행 방법](#실행-방법)
- [배포 구조](#배포-구조)
- [운영 스크립트](#운영-스크립트)
- [API 문서](#api-문서)

---

## 프로젝트 소개

**GodLife**는 사용자가 루틴(계획)을 설정하고, 매일 인증하며 목표를 달성해 나가는 갓생 관리 플랫폼입니다.
루틴 인증, 챌린지 참여, 레벨 성장, 불꽃 콤보 시스템 등을 통해 꾸준한 습관 형성을 지원합니다.
관리자와의 실시간 1:1 문의 채팅 기능도 제공합니다.

- **서비스 도메인:** [godlifelog.com](https://godlifelog.com)
- **백엔드 프레임워크:** Spring Boot 3.4.2
- **Java 버전:** Java 17

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| **Framework** | Spring Boot 3.4.2 |
| **언어** | Java 17 |
| **보안** | Spring Security, JWT (Access 5분 / Refresh 24시간) |
| **ORM** | MyBatis 3.0.4 |
| **데이터베이스** | MariaDB 10.11 |
| **캐시 / 메시지 큐** | Redis 7.2 (Spring Data Redis + Redisson) |
| **실시간 통신** | WebSocket (순수 ws), STOMP (SockJS) |
| **이메일** | Naver SMTP (Spring Mail) |
| **API 문서** | SpringDoc OpenAPI (Swagger) 2.8.4 |
| **프록시** | Nginx (TLS 1.2/1.3, HTTP/2, Let's Encrypt) |
| **인프라** | Docker, Docker Compose |
| **배포 전략** | Blue-Green 무중단 배포 |
| **빌드 도구** | Gradle |

---

## 시스템 아키텍처

### 전체 구성도

```
[인터넷]
    │
    │ 80 (HTTP → 301 HTTPS 리다이렉트)
    │ 443 (HTTPS / TLS 1.2+1.3 / HTTP2)
    ▼
[Nginx] ─────────────── godlife-network (Docker Bridge)
    │
    ├── /api/*        → http://$service_url  (blue:9090 또는 green:9090)
    ├── /ws-stomp     → WebSocket STOMP 업그레이드 (24시간 타임아웃)
    └── /             → React SPA 정적 파일 서빙
    │
    ├── [blue:9090]   Spring Boot (현재 활성 or 대기)
    └── [green:9090]  Spring Boot (현재 대기 or 활성)
            │
            ├── [mariadb:3306]   MariaDB 10.11  (외부 미노출)
            └── [redis:6379]     Redis 7.2      (외부 미노출)
```

### 애플리케이션 내부 구조

```
[클라이언트 (godlifelog.com)]
        │
        ├── REST API (HTTPS)
        │       └── JWT 인증 (Access Token: Header / Refresh Token: HttpOnly Cookie)
        │
        └── WebSocket
                ├── ws-chat  → 관리자 1:1 채팅 (순수 WebSocket)
                └── ws-stomp → QnA 실시간 알림 (STOMP + SockJS)

[Spring Boot 3.4.2 서버]
  ├── Security: Stateless JWT 필터 체인 (BCrypt 암호화)
  ├── Controller → Service → MyBatis Mapper → MariaDB
  ├── Redis: 캐시 Warm-up / 토큰 관리 / 이메일 인증 / QnA 큐
  ├── Scheduler: 자정·2시간·5분 주기 배치 작업
  ├── QnaQueueListener: Redis Queue 기반 QnA 자동 매칭 데몬
  └── Redisson: 분산 락 (Race Condition 방지)
```

---

## 주요 기능

### 회원 시스템
- 회원가입 / 로그인 / 로그아웃
- JWT 기반 인증 (Access 토큰 5분, Refresh 토큰 24시간)
- 아이디 / 이메일 중복 검사 (DB 실시간 검증)
- 이메일 인증 (Naver SMTP + Redis TTL)
- 아이디 찾기 / 비밀번호 초기화
- 신고 누적 시 계정 정지 처리

### 루틴 (Plan)
- 루틴 생성 / 수정 / 삭제
- 반복 요일, 중요도, 직업/목표 카테고리 설정
- 루틴 공유 및 포크(복제)
- 매일 인증 처리 및 조기 완료
- 루틴 완료 후 후기 작성
- 좋아요 기능

### 챌린지
- 챌린지 목록 / 상세 조회 및 검색
- 챌린지 참여 및 인증
- 관리자 챌린지 생성 / 수정 / 종료 (5분 주기 자동 상태 업데이트)

### 레벨 & 불꽃 (성장 시스템)
- 루틴 인증 시 경험치 적용 및 레벨업
- 불꽃(연속 달성) 콤보 시스템
- 자정 스케줄러: 인증률 90% 미만 시 경험치 감소 / 콤보 초기화

### 1:1 문의 (QnA)
- 문의 등록 / 조회 / 수정 / 삭제
- Redis Queue 기반 자동 매칭 데몬 (관리자 자동 배정)
- STOMP WebSocket을 통한 실시간 답변 알림
- 관리자 접속 상태 관리

### 실시간 채팅 (관리자)
- 관리자와의 1:1 채팅방 생성
- 순수 WebSocket 기반 실시간 채팅

### 관리자 기능
- 회원 목록 조회 / 권한 변경 / 정지 처리
- 루틴 / 챌린지 / 신고 관리
- 공지사항 / FAQ / 서비스센터 관리
- 관리자 통계 (서비스센터 처리 현황 등)

### 기타
- 공지사항 / FAQ 조회
- 이미지 업로드
- 마이페이지 정보 수정
- 카테고리 조회 (Redis 캐싱, 앱 시작 시 Warm-up)
- AOP 기반 API 응답 시간 측정 (`@LogExecutionTime`)

---

## 프로젝트 구조

```
src/main/java/com/godLife/project/
├── ProjectApplication.java          # 메인 클래스 (@EnableScheduling)
├── common/
│   ├── anotation/                   # @LogExecutionTime 커스텀 어노테이션
│   └── aop/                         # 응답 시간 측정 AOP
├── config/
│   ├── SecurityConfig.java          # Spring Security 설정
│   ├── RedisConfig.java             # Redis / CacheManager 설정
│   ├── RedissonConfig.java          # 분산 락 설정
│   ├── CorsMvcConfig.java           # CORS 설정
│   └── websocket/                   # WebSocket / STOMP 설정
├── controller/                      # REST 컨트롤러
│   ├── AdminController/             # 관리자 전용 API
│   ├── websocket/                   # WebSocket 컨트롤러
│   └── jwtController/               # 토큰 재발급
├── service/
│   ├── interfaces/                  # 서비스 인터페이스
│   └── impl/                        # 서비스 구현체
│       └── adminImpl/               # 관리자 서비스
├── mapper/                          # MyBatis 매퍼 인터페이스
├── dto/                             # 데이터 전송 객체
├── enums/                           # 열거형 상수
├── exception/                       # 커스텀 예외 클래스
├── handler/                         # 예외 핸들러, WebSocket 핸들러
├── jwt/                             # JWT 필터 (JWTFilter, LoginFilter, LogoutFilter)
├── listener/                        # Redis / WebSocket / QnA 큐 리스너
├── runner/                          # 앱 시작 시 캐시 Warm-up
├── scheduler/                       # 자정 / 2시간 / 5분 배치 스케줄러
├── utils/                           # HtmlSanitizer (jsoup)
└── valid/                           # 커스텀 유효성 검사 어노테이션

src/main/resources/
├── mybatis/
│   ├── mybatis-config.xml
│   └── mapper/                      # MyBatis XML 매퍼
│       ├── AdminMapper/
│       ├── autoMatch/
│       ├── StatsMapper/
│       └── *.xml (User, Plan, Challenge, Qna, ...)
├── config/
│   └── logback-spring.xml           # 로깅 설정
└── application*.properties          # 환경별 설정 파일
```

---

## 환경 설정

### Spring 프로파일 구조

| 프로파일 | 설명 |
|----------|------|
| `local` | 로컬 개발 환경 (localhost:9090, DB: localhost:3306) |
| `blue` | Blue 서버 (Docker 환경, 0.0.0.0:9090) |
| `green` | Green 서버 (Docker 환경, 0.0.0.0:9090) |
| `common` | 공통 설정 (MyBatis, 로깅, CORS, Jackson 등) |
| `secret` | 민감 정보 (DB 비밀번호, JWT Secret, 메일 정보) |

### .env 환경 변수 (서버 배포용)

```bash
DB_ROOT_PASSWORD=<root_password>
DB_NAME=god_life_db
DB_USER=godlife
DB_PASSWORD=<password>
REDIS_HOST=redis
REDIS_PORT=6379
DOCKER_USERNAME=<dockerhub_username>
IMAGE_TAG=latest
TZ=Asia/Seoul
```

### application-secret.properties 주요 설정

```properties
# 데이터베이스
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
spring.datasource.url=jdbc:mariadb://<host>:3306/god_life_db
spring.datasource.username=godlife
spring.datasource.password=<password>

# Redis
spring.redis.host=${SPRING_REDIS_HOST:localhost}
spring.redis.port=${SPRING_REDIS_PORT:6379}
spring.redis.password=<password>

# JWT
spring.jwt.secret=<secret>

# 메일 (Naver SMTP)
spring.mail.host=smtp.naver.com
spring.mail.port=465
spring.mail.username=<email>
spring.mail.password=<password>
```

---

## 실행 방법

### 로컬 개발 환경

```bash
# 1. MariaDB + Redis 컨테이너 실행
docker-compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Gradle 빌드 후 실행

```bash
./gradlew clean build
java -jar build/libs/project-0.0.1-SNAPSHOT.jar
```

---

## 배포 구조

### Docker Compose 레이어 분리

```
app/
├── docker-compose-infra.yml   # MariaDB + Redis
├── docker-compose-app.yml     # Blue + Green (Spring Boot)
└── docker-compose-nginx.yml   # Nginx (리버스 프록시)
```

모든 컨테이너는 `godlife-network` (외부 Docker 브리지 네트워크) 안에서 통신합니다.
MariaDB / Redis / Blue / Green 컨테이너는 외부에 포트를 노출하지 않으며, Nginx만 80/443 포트를 공개합니다.

### Blue-Green 무중단 배포 흐름

```
deploy.sh 실행
    │
    ├── 1. 현재 활성 컨테이너 확인 (blue? green?)
    ├── 2. 반대 색상을 TARGET으로 설정
    ├── 3. 새 Docker 이미지 Pull
    ├── 4. TARGET 컨테이너 기동 (docker compose up -d)
    ├── 5. 헬스체크 → GET /api/hc (최대 60초 재시도)
    ├── 6. ✅ 성공 시: nginx/conf.d/service-env.inc 수정
    │           set $service_url {TARGET}:9090;
    │           nginx -s reload  (무중단 트래픽 전환)
    └── 7. 이전 컨테이너 stop (삭제 안 함 → 롤백 대비)
```

### 롤백 흐름

```
rollback.sh 실행
    │
    ├── 1. 중지된(exited) 컨테이너 확인
    ├── 2. 이전 컨테이너 start (새 이미지 pull 없음)
    ├── 3. 헬스체크 (최대 30초)
    ├── 4. service-env.inc 수정 → nginx reload
    └── 5. 현재 활성 컨테이너 stop
```

### Nginx 라우팅 규칙

| 경로 | 처리 |
|------|------|
| `/api/*` | Spring Boot로 프록시 (`$service_url`) |
| `/ws-stomp` | WebSocket STOMP 업그레이드 (24시간 타임아웃) |
| `/` | React SPA 정적 파일 서빙 |
| `/.well-known/` | Let's Encrypt 인증 |
| `/\.` | 숨김 파일 접근 차단 |

### 보안 헤더

```
Strict-Transport-Security  (HSTS, 1년)
X-Frame-Options: SAMEORIGIN
X-Content-Type-Options: nosniff
X-XSS-Protection
Content-Security-Policy    (WSS 허용 포함)
server_tokens off          (Nginx 버전 노출 차단)
```

---

## 운영 스크립트

| 스크립트 | 실행 시점 | 역할 |
|----------|----------|------|
| `scripts/deploy.sh` | 배포 시 | Blue-Green 무중단 배포 |
| `scripts/rollback.sh` | 장애 시 | 이전 버전 즉시 롤백 |
| `scripts/backup.sh` | cron: 매일 10:00 | .env + MariaDB 볼륨 백업 (30일 보관) |
| `scripts/security-check.sh` | cron: 매일 23:50 | 로그인 실패 / fail2ban / UFW / 디스크 점검 |
| `scripts/test-health.sh` | 수동 | 헬스체크 환경 진단 |

### 서버 보안 구성

```
UFW 방화벽    → 22 (SSH), 80 (HTTP), 443 (HTTPS) 만 허용
SSH           → Root 로그인 차단, 공개키 인증만 허용, MaxAuthTries=3
Fail2ban      → SSH 3회 실패 시 2시간 IP 차단
Docker 로그   → json-file, 10MB × 3개 로테이션
자동 업데이트 → unattended-upgrades 활성화
```

---

## 스케줄러

| 주기 | 작업 내용 |
|------|----------|
| 매일 자정 `0 0 0 * * ?` | 인증률 90% 미만 유저 경험치 감소, 콤보 초기화, 불꽃 상태 초기화, 탈퇴 계정 삭제 |
| 2시간마다 `0 0 0/2 * * ?` | 만료된 Refresh Token 삭제 |
| 5분마다 `0 0/5 * * * ?` | 챌린지 상태 자동 종료 업데이트 |

---

## 권한 체계

| authorityIdx | 역할 |
|-------------|------|
| 1 | 일반 유저 |
| 2 ~ 6 | 관리자 (등급별) |
| 7 | 최고 관리자 |

---

## API 문서

애플리케이션 실행 후 Swagger UI에서 전체 API를 확인할 수 있습니다.

```
http://localhost:9090/swagger-ui/index.html
```

### 주요 API 엔드포인트

| 분류 | 경로 | 설명 |
|------|------|------|
| 회원 | `POST /api/user/login` | 로그인 |
| 회원 | `POST /api/user` | 회원가입 |
| 토큰 | `POST /reissue` | Access 토큰 재발급 |
| 루틴 | `GET /api/plan` | 루틴 목록 조회 |
| 루틴 | `POST /api/plan` | 루틴 생성 |
| 챌린지 | `GET /api/challenges` | 챌린지 목록 조회 |
| 문의 | `POST /api/qna/auth` | 문의 등록 |
| 이미지 | `POST /api/upload/auth` | 이미지 업로드 |
| 관리자 | `GET /api/admin/users` | 회원 목록 조회 |
| 헬스체크 | `GET /api/hc` | 서버 상태 확인 |
