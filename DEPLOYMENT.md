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