# 배포 절차

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