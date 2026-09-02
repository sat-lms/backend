# 내 제출 내역 목록 정책 (Issue #41)

이 문서는 Issue #41에서 확정된 보완 계약이다. 저장소에서 기존 PDF 원문은 확인하지 못했으므로 PDF 정책을 전사한 문서가 아니다.

`GET /api/v1/members/me/submissions`는 과제를 기준으로 현재 학생의 제출을 LEFT JOIN한다. `includeNotSubmitted` 기본값은 `true`이며 `false`이면 제출이 존재하는 과제만 DB에서 조회한다.

## 상태

- 제출 존재, `isLate=false`: `SUBMITTED`
- 제출 존재, `isLate=true`: `LATE`
- 미제출, 현재 시각이 마감 시각 이하: `IN_PROGRESS`
- 미제출, 마감 후지만 지각 제출 허용: `IN_PROGRESS`
- 미제출, 마감 후이며 지각 제출 불허: `NOT_SUBMITTED`

한 요청은 application Clock에서 기준 Instant를 한 번 읽는다. 저장된 `isLate`는 조회 중 재계산하거나 변경하지 않는다. 과제 목록 API에서 STUDENT에게도 같은 계산기를 적용하며 ADMIN에게는 `submissionStatus: null`을 반환한다.

## 정렬과 응답

- 기본 `dueAtDesc`: `dueAt DESC, assignmentId DESC`
- `dueAtAsc`: `dueAt ASC, assignmentId ASC`
- `submittedAtDesc`: `submittedAt DESC NULLS LAST, assignmentId DESC`

`submittedAt`은 현재 Submission의 `updatedAt`, 즉 마지막 제출·재제출 시각이다. 현재 운영 코드에서 Submission을 수정하는 경로는 재제출뿐이다. 향후 다른 수정 경로가 추가되면 이 의미를 재검토해야 한다.

미제출 행은 `submissionId`, `submittedAt`, `textContent`, `createdAt`, `updatedAt`이 null이고 `isLate`는 false이며 `fileNames`와 `attachments`는 빈 배열이다. 기존 응답 필드는 유지되지만 기본 조회에 미제출 행이 추가되고 기존 필드가 null일 수 있으므로 완전한 무중단 호환 변경은 아니다.

허용되는 쿼리 값은 `includeNotSubmitted=true|false`와 위 세 정렬 값뿐이다. 빈 값, 복수 값 및 그 밖의 값은 400을 반환한다.
