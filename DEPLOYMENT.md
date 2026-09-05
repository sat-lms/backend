# 배포 절차

## 실제 S3 통합 테스트

기본 `./gradlew clean test`는 `s3-integration` 태그를 제외하므로 실제 AWS에 접근하지 않습니다. 실제 S3와
PostgreSQL Testcontainers를 함께 검증하려면 다음 환경변수가 **현재 PowerShell 프로세스**에 있어야 합니다.

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`
- `AWS_S3_BUCKET`
- `AWS_S3_PRESIGNED_EXPIRATION_MINUTES`

Gradle 테스트 JVM에 `.env`가 자동 전달된다고 가정하지 마세요. `.env`를 열거나 값을 출력하지 않고
`Test-Path Env:AWS_ACCESS_KEY_ID`처럼 각 이름의 존재 여부만 확인할 수 있습니다. 실제 실행을 강제하려면:

```powershell
.\gradlew.bat --no-daemon s3IntegrationTest -PrequireS3Integration=true
```

`requireS3Integration` 없이 실행하면 환경변수 누락 시 테스트가 assumption skip될 수 있습니다. 실행 후
`build/test-results/s3IntegrationTest/TEST-*.xml`에서 `skipped="0"`과 실제 tests 수를 확인해야 합니다.
테스트는 UUID 기반으로 자신이 생성한 정확한 객체 키만 추적해 삭제하며 버킷이나 도메인 prefix 전체를
삭제하지 않습니다. 객체 존재 확인과 다운로드에는 `s3:GetObject`, 업로드·정리에는 `s3:PutObject`,
`s3:DeleteObject`가 필요합니다. 버킷 보안 점검에는 추가로 `s3:GetBucketPublicAccessBlock` 읽기 권한이
필요합니다. 테스트는 버킷 정책이나 Public Access Block 설정을 변경하지 않습니다.

## 인증 API 요청 제한

로그인은 계정 존재 여부, 비밀번호 일치 여부, 비승인 회원 상태를 구분하지 않고 모두 같은 401 응답을
반환합니다. 존재하지 않는 계정에도 애플리케이션 시작 시 한 번 생성한 dummy BCrypt hash로 검증을
수행해 BCrypt 수행 여부에서 생기는 큰 비용 차이를 완화합니다. 이는 정확한 처리 시간이 완전히 같음을
보장하지는 않습니다.

애플리케이션은 다음 두 공개 인증 API에 IP별 인메모리 요청 제한을 적용합니다.

| 경로 | 초기 운영 정책 |
| --- | --- |
| `POST /api/v1/auth/login` | IP당 1분에 10회 |
| `POST /api/v1/auth/signup` | IP당 1시간에 5회 |

이 횟수는 Issue 및 API 명세에 확정 수치가 없어 선택한 초기 운영 정책입니다. 로그인과 회원가입은 서로
독립된 버킷을 사용합니다. 한도를 초과하면 HTTP 429와 함께 `Retry-After` 헤더로 현재 버킷에서 다시
요청할 수 있을 때까지 남은 초를 반환합니다. `X-RateLimit-Limit`과 `X-RateLimit-Remaining`도 제공합니다.

기본값은 다음 환경변수로 조정할 수 있습니다. capacity, period, cache 크기와 만료 시간은 모두 양수여야
하며 잘못된 값이면 애플리케이션 기동이 실패합니다.

| 환경변수 | 기본값 | 의미 |
| --- | --- | --- |
| `AUTH_LOGIN_RATE_LIMIT_CAPACITY` | `10` | 로그인 허용 횟수 |
| `AUTH_LOGIN_RATE_LIMIT_PERIOD` | `1m` | 로그인 제한 주기 |
| `AUTH_SIGNUP_RATE_LIMIT_CAPACITY` | `5` | 회원가입 허용 횟수 |
| `AUTH_SIGNUP_RATE_LIMIT_PERIOD` | `1h` | 회원가입 제한 주기 |
| `AUTH_RATE_LIMIT_CACHE_MAXIMUM_SIZE` | `10000` | 보관할 IP/인증 경로 버킷의 최대 개수 |
| `AUTH_RATE_LIMIT_CACHE_EXPIRE_AFTER_ACCESS` | `2h` | 마지막 접근 후 버킷 만료 시간 |

현재는 신뢰할 리버스 프록시가 정의되어 있지 않으므로 `X-Forwarded-For`와 `Forwarded` 헤더를 신뢰하지
않고 직접 연결의 `remoteAddr`만 사용합니다. 임의의 전달 헤더를 신뢰하면 요청자가 헤더를 바꿔 제한을
우회할 수 있습니다. 리버스 프록시를 도입할 때는 신뢰할 프록시 주소를 명시적으로 제한한 뒤 IP 해석
정책을 함께 변경해야 합니다.

이 제한 상태는 애플리케이션 인스턴스 메모리에만 존재합니다. 여러 인스턴스를 운영하면 각 인스턴스가
별도의 허용 횟수를 가지므로 전체 서비스 기준 제한이 아닙니다. 인스턴스 전체에서 공유되는 제한이
필요하면 Redis 같은 외부 저장소 기반 버킷을 별도 설계해야 합니다.

## 데이터베이스 연결 풀

HikariCP의 최대 풀 크기는 `10개`, 연결 획득 제한 시간은 `30000ms`(30초)입니다. 기존 기본 동작을 명시한
값이며, S3 네트워크 작업과 삭제 재시도를 DB 트랜잭션 밖으로 분리해 연결 점유 시간을 줄이는 것이 목적입니다.
운영 부하와 DB의 허용 연결 수를 측정하지 않은 채 풀 크기만 확대하지 마세요.

## JWT_SECRET 필수 설정

`JWT_SECRET`은 애플리케이션 기동에 필요한 필수 환경 변수입니다. Docker Compose뿐 아니라 로컬 IDE 또는
`./gradlew bootRun`으로 실행할 때도 반드시 설정해야 합니다. 자동 배포가 실패하지 않도록 이 변경을 병합하기 전에
운영 담당자가 배포 환경에 키를 준비해야 합니다.

충분한 엔트로피를 가진 키를 생성하려면 다음과 같이 암호학적으로 안전한 난수 32바이트 이상을 Base64로 인코딩할 수
있습니다. 생성된 출력은 예시 명령을 실행한 안전한 환경에서만 확인하고 비밀 저장소에 설정합니다.

```shell
openssl rand -base64 32
```

실제 키는 Git에 커밋하거나 채팅 및 로그에 남기지 마세요. 공개 기본 키를 사용했거나 과거 사용 여부가 불명확한
환경은 반드시 새 키로 교체해야 합니다. 키 교체 후 기존 JWT는 모두 무효화되므로 사용자 재로그인이 필요합니다.
여러 애플리케이션 인스턴스를 운영한다면 모든 인스턴스에 같은 새 키를 일관되게 설정한 뒤 배포 및 재시작해야 합니다.
이 코드 변경만으로 운영 환경의 키 교체가 완료되는 것은 아닙니다.

## 최초 구축

DB와 애플리케이션을 함께 처음 띄울 때:

```
docker compose up -d --build
```

- `postgres` 서비스가 healthy(`pg_isready`)로 판정된 이후에만 `app` 서비스가 시작됩니다
  (`depends_on.postgres.condition: service_healthy`).
- `app` 시작 시 Flyway가 자동으로 마이그레이션을 수행합니다(`spring.flyway.enabled=true`).

## 코드 변경 후 재배포

`postgres`는 그대로 두고 `app` 서비스만 재빌드·재생성합니다:

```
docker compose up -d --build app
```

- `postgres` 컨테이너는 재생성되지 않으므로 데이터가 보존됩니다(`postgres-data` named volume은
  `docker compose down --volumes`로 명시적으로 지우지 않는 한 유지됩니다).
- `app` 컨테이너가 재시작되면서 Flyway가 다시 실행되지만, 이미 적용된 마이그레이션은
  스킵되고 새로 추가된 마이그레이션만 적용됩니다.

## DB 접속

postgres 포트는 호스트에 노출되지 않습니다. Docker가 iptables를 직접 조작해 ufw 규칙을
우회할 수 있어, `127.0.0.1` 바인딩조차 SSH로 서버에 접근 가능한 사람에게는 열려 있는 것과
같기 때문에 포트 자체를 아예 게시하지 않습니다. DB 확인이 필요하면 컨테이너 내부로 직접
접속하세요:

```
docker exec -it team-postgres psql -U $DB_USERNAME -d $DB_NAME
```

## 환경별 설정 (Spring 프로파일)

설정은 공통 파일과 프로파일 파일로 나뉘어 있습니다.

| 파일 | 역할 |
| --- | --- |
| `src/main/resources/application.yaml` | 전 환경 공통 (datasource 플레이스홀더, JPA, multipart, flyway, jwt, aws) |
| `src/main/resources/application-dev.yaml` | 로컬 개발 — SQL 로그 on, `com.sat.lms` DEBUG, CORS에 localhost 포함 |
| `src/main/resources/application-prod.yaml` | 배포 서버 — SQL 로그 off, root INFO, CORS는 배포 도메인만 |
| `src/test/resources/application-test.yaml` | 테스트/CI — 더미 datasource, 테스트용 jwt 시크릿 |

**기본 프로파일(`spring.profiles.default`)은 두지 않습니다.** 프로파일을 명시하지 않고 기동하면
`application.yaml`의 공통 설정만 적용되어, 어떤 경로로 띄우든 운영에 dev 설정이 새지 않습니다.
대신 각 실행 경로가 자기 프로파일을 직접 선언합니다.

| 실행 방식 | 프로파일 | 결정 방식 |
| --- | --- | --- |
| `./gradlew bootRun` | `dev` | `build.gradle` bootRun 태스크의 `spring.profiles.active` |
| IDE 실행 | `dev` | 아래 "IDE 실행 구성" 참고 |
| `docker compose up app` | `prod` | `docker-compose.yml`의 `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}` |
| `docker run` (compose 없이) | `prod` | `Dockerfile`의 `ENV SPRING_PROFILES_ACTIVE=prod` |
| `./gradlew test`, CI | `test` | `build.gradle` test 태스크의 `spring.profiles.active` |

컨테이너 기본값이 `prod`이므로 **서버 `.env`에 `SPRING_PROFILES_ACTIVE`를 넣지 않아도 운영은 prod로 뜹니다.**
반대로 로컬에서 컨테이너를 dev 설정으로 띄우고 싶으면 개인 `.env`에 `SPRING_PROFILES_ACTIVE=dev`를 추가하세요.
(이 경우 `.env`를 읽는 주체는 애플리케이션이 아니라 docker compose입니다.)

### IDE 실행 구성

IntelliJ의 **Settings → Build, Execution, Deployment → Build Tools → Gradle → Build and run using** 값에 따라 갈립니다.

- `Gradle`(기본값): Spring Boot 실행 구성이 `bootRun`으로 위임되므로 위 설정이 그대로 적용됩니다. 추가 작업이 없습니다.
- `IntelliJ IDEA`로 바꾼 경우: 실행 구성의 **Active profiles**에 `dev`를 입력하세요.
  Community 에디션이라 해당 칸이 없다면 VM options에 `-Dspring.profiles.active=dev`를 넣거나
  Environment variables에 `SPRING_PROFILES_ACTIVE=dev`를 설정하면 됩니다.

프로파일 지정을 빠뜨리면 **앱은 에러 없이 기동되지만** SQL 로그가 찍히지 않고
CORS 허용 목록이 비어 로컬 프론트 요청이 403으로 막힙니다. 증상이 이렇다면 이 설정을 먼저 확인하세요.

`.env`에 `SPRING_PROFILES_ACTIVE`를 넣는 방식은 애플리케이션을 직접 실행할 때는 동작하지 않습니다.
`spring-dotenv`가 프로파일이 확정된 뒤에 값을 주입하기 때문이며, 프로파일은 실제 환경변수나 `-D` 옵션으로만 지정할 수 있습니다.

배포 후 실제로 어떤 프로파일이 적용됐는지는 기동 로그로 확인합니다:

```
docker compose logs app | grep "profile is active"
```

### CORS 오리진 변경

허용 오리진은 코드가 아니라 설정에 있습니다. 프론트 도메인이 바뀌면 서버 `.env`에
`CORS_ALLOWED_ORIGINS`(여러 개면 콤마 구분)를 설정하고 `docker compose up -d app`으로 재기동하면 됩니다.
운영 프로파일에는 localhost 오리진이 포함되지 않습니다.

## DB_URL 관련 참고

`.env`의 `DB_URL`(`jdbc:postgresql://localhost:5432/...`)은 애플리케이션을 Docker 밖에서
직접 실행할 때(IDE, `./gradlew bootRun` 등)만 사용됩니다. `docker compose`로 `app`을 띄우면
컨테이너 안에서 `localhost`가 `app` 컨테이너 자신을 가리키게 되므로, `docker-compose.yml`이
`app` 서비스의 `DB_URL` 환경변수를 `jdbc:postgresql://postgres:5432/${DB_NAME}`로 자동
재구성해서 넘깁니다. `.env`의 값을 직접 고칠 필요는 없습니다.

## CI/CD

`.github/workflows/ci.yml`(CI)과 `.github/workflows/deploy.yml`(CD) 두 워크플로우로 구성되어
있습니다.

### 동작 방식

1. **PR을 `dev`로 열거나 갱신** → `ci.yml`이 `pull_request` 이벤트로 실행되어 `./gradlew test`를
   돌립니다. 테스트가 하나라도 실패하면 워크플로우가 실패 상태가 되고, 실패한 테스트의 상세
   리포트는 워크플로우 실행 화면의 Artifacts에서 `test-results`로 내려받을 수 있습니다.
2. **PR이 `dev`에 머지되어 커밋이 push됨** → `ci.yml`이 이번엔 `push` 이벤트로 다시 한 번
   실행됩니다(머지 커밋 자체를 검증하기 위함 — PR 헤더 커밋과 머지 커밋은 SHA가 다릅니다).
3. **그 `ci.yml` 실행이 성공으로 끝남** → `deploy.yml`이 `workflow_run` 트리거로 자동 실행되어
   배포 서버에 SSH 접속 후 `git pull origin dev && docker compose up -d --build app`을
   실행합니다. `postgres`는 건드리지 않으므로 DB 데이터는 그대로 유지됩니다.
4. 테스트가 실패하면 `deploy.yml`은 아예 트리거되지 않습니다(`workflow_run`의
   `conclusion == 'success'` 조건).

### 필요한 GitHub Secrets

레포 Settings → Secrets and variables → Actions 에 아래 3개를 등록해야 `deploy.yml`이
동작합니다.

| Secret | 용도 |
|---|---|
| `SSH_HOST` | 배포 서버(Lightsail)의 IP 또는 도메인 |
| `SSH_USERNAME` | SSH 접속 계정명 |
| `SSH_PRIVATE_KEY` | 위 계정으로 접속할 SSH private key (pem 키 전체 내용) |

### 자동 배포가 실패했을 때 수동 배포

CD 워크플로우가 실패했거나 아직 Secrets가 설정되지 않은 상황이라면, 기존처럼 서버에 직접
SSH 접속해서 수동으로 배포합니다:

```
cd ~/backend
git pull origin dev
docker compose up -d --build app
```

### 브랜치 보호 규칙 (GitHub Settings에서 직접 설정 필요)

아래는 코드로 구현할 수 없는, 레포 Settings에서 직접 켜야 하는 항목입니다.

- Settings → Branches → `dev`에 브랜치 보호 규칙 추가
- **Require status checks to pass before merging** 체크 후, 필수 체크로 CI 워크플로우의 job
  이름(`test`, 워크플로우 이름 기준으로는 `CI`)을 지정
- (선택, 권장) **Do not allow force pushes**, **Do not allow deletions**도 함께 켜기
