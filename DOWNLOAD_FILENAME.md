# 첨부 다운로드 원본 파일명

공지·과제·제출 다운로드 URL은 기존 회원 상태·권한 및 실제 도메인 연결 검증을 통과한
Attachment의 `storageKey`와 DB `originalName`으로 발급합니다. API 경로와
`downloadUrl`, `expiresIn`, `originalName` 응답은 그대로입니다.

S3 GetObjectRequest에 `responseContentDisposition`을 지정한 뒤 Presign합니다.
UUID 저장명, S3 객체, DB 값은 변경하지 않습니다. URL 만료시간과 응답의 expiresIn은
기존처럼 동일한 Duration에서 계산합니다. URL은 저장하거나 로그에 출력하지 않습니다.

## 헤더와 레거시 값

`attachment` disposition에 ASCII `filename`과 UTF-8 `filename*`를 지정합니다.
Spring 6.2.19 ContentDisposition의 quoted-string escaping과 RFC 5987 인코딩을
사용합니다. Spring이 기본 생성하는 RFC 2047 encoded-word fallback 대신 일반
ASCII fallback을 사용하며, UTF-8 filename* 부분은 Spring이 생성한 값을 사용합니다.
공백은 `%20`이고 파일명에 URLEncoder를 적용하지 않습니다. S3 SDK가 서명 과정에서
쿼리 값을 인코딩하므로 URL 발급 이후 파라미터를 붙이지 않습니다.

- 정상 원본 이름: 한글·영문·숫자·공백·괄호·여러 점·확장자 대소문자를 filename*에 보존합니다.
- 정상 ASCII 이름: filename fallback에도 그대로 사용하며 따옴표는 escape합니다.
- 정상 비ASCII 이름: ASCII fallback은 `download`와 원본 이름의 안전한 확장자입니다.
- 비정상 레거시 이름: null/빈 값/공백만 있는 값, 앞뒤 공백, 255자 초과, 경로 구분자,
  `.`/`..` 포함, ISO 제어문자, 잘못된 surrogate는 `download`와 storageKey의 안전한
  확장자로 대체합니다. 확장자는 영문·숫자 1~10자만 허용하며 없으면 `download`입니다.

이 처리는 다운로드 헤더에만 적용합니다. 업로드 검증과 DB originalName 응답은 바꾸지 않습니다.
기존 첨부도 새 URL을 발급하면 적용되며, 이미 발급한 URL에는 소급 적용되지 않습니다.

## 수동 AWS 및 브라우저 확인

이번 작업에서 실제 AWS 테스트는 실행하지 않습니다. 사용자가 별도 테스트 버킷과
기존 테스트용 환경변수를 준비한 뒤 선택적으로 다음 명령을 실행할 수 있습니다.
자격증명이나 Presigned URL을 콘솔·로그·공유 자료에 출력하지 마세요.

```powershell
.\gradlew.bat s3IntegrationTest --tests com.sat.lms.global.storage.S3FileStorageIntegrationTest --tests com.sat.lms.attachment.AttachmentDomainS3PostgreSqlIntegrationTest -PrequireS3Integration=true
```

이 opt-in 테스트는 자체 테스트 객체를 업로드하고 정리합니다. 기존 운영 객체를 사용하지
마세요. 저장 계층 테스트는 한글 이름의 실제 GET Content-Disposition과 원본 바이트를,
도메인 테스트는 세 첨부 도메인의 GET 헤더·원본 바이트·권한·연결을 확인합니다.

브라우저 확인은 수정 버전이 적용된 테스트 환경에서 공지·과제·제출 각각 새 URL을
발급하여 진행합니다. `과제 안내 (최종).pdf` 등 한글·공백·괄호 파일을 포함하고,
원본 바이트와 저장 파일명을 데스크톱·모바일·카카오톡 내부 브라우저에서 각각 확인합니다.
실제 GET 응답에는 `attachment`, ASCII filename, UTF-8 filename*가 있어야 합니다.

프론트 main의 NoticeDetailPage, AssignmentDetailPage, AdminSubmissionsPage는
downloadUrl을 window.open으로 열며 해당 경로에 download 속성·Blob 처리가 없습니다.
배포된 프론트 버전 및 실제 브라우저 동작은 별도 확인이 필요합니다. 브라우저가
filename*를 무시하면 ASCII fallback이 사용될 수 있으므로 모바일 해결을 모의 테스트만으로
확정하지 않습니다.

## 변경 파일

신규:

- `DOWNLOAD_FILENAME.md`
- `src/main/java/com/sat/lms/global/storage/DownloadContentDisposition.java`
- `src/test/java/com/sat/lms/global/storage/DownloadContentDispositionTest.java`

수정:

- `src/main/java/com/sat/lms/global/storage/FileStorage.java`
- `src/main/java/com/sat/lms/global/storage/S3FileStorage.java`
- `src/main/java/com/sat/lms/notice/service/NoticeAttachmentService.java`
- `src/main/java/com/sat/lms/assignment/service/AssignmentAttachmentService.java`
- `src/main/java/com/sat/lms/submission/service/SubmissionService.java`
- `src/test/java/com/sat/lms/global/storage/S3FileStorageTest.java`
- `src/test/java/com/sat/lms/global/storage/S3FileStorageIntegrationTest.java`
- `src/test/java/com/sat/lms/attachment/AttachmentDomainS3PostgreSqlIntegrationTest.java`
- `src/test/java/com/sat/lms/notice/service/NoticeAttachmentServiceTest.java`
- `src/test/java/com/sat/lms/assignment/service/AssignmentAttachmentServiceTest.java`
- `src/test/java/com/sat/lms/submission/service/SubmissionServiceTest.java`
- `src/test/java/com/sat/lms/notice/repository/NoticePostgreSqlIntegrationTest.java`
- `src/test/java/com/sat/lms/assignment/repository/AssignmentPostgreSqlIntegrationTest.java`
- `src/test/java/com/sat/lms/submission/repository/SubmissionPostgreSqlIntegrationTest.java`
- `src/test/java/com/sat/lms/member/service/MemberGuardPostgreSqlIntegrationTest.java`
