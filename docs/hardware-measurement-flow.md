# Hardware Measurement Flow Guide

이 문서는 FeetFit 하드웨어가 백엔드와 연동할 때 따라야 하는 측정 진행 흐름을 정리한다.

## 핵심 원칙

- 하드웨어는 WebSocket을 구독하지 않는다.
- 하드웨어는 백엔드에서 전달받은 `measurementSessionId`와 `Authorization` 토큰을 저장해두고, 이후 백엔드 API 호출에 그대로 사용한다.
- 프론트와 백엔드는 WebSocket STOMP로 측정 상태를 주고받는다.
- 하드웨어는 측정 단계가 바뀔 때마다 백엔드의 상태 변경 API를 호출한다.
- 백엔드는 상태 변경을 저장한 뒤 프론트가 구독 중인 `/topic/measurements/{measurementSessionId}`로 상태 메시지를 발행한다.
- 사용자의 발이 ArUco 마커를 가린 경우에는 `FAILED` 대신 `WAITING_FOR_RECAPTURE`를 전달한다. 상세 계약은 아래 재촬영 절을 따른다.
- 그 외 측정을 계속할 수 없는 오류는 `FAILED` 상태와 실패 원인을 백엔드로 전달한다.

## 1. 백엔드가 하드웨어를 깨우는 요청

프론트가 측정 세션 생성 API를 호출하면, 백엔드는 사용자에게 연결된 디바이스를 확인한 뒤 하드웨어 서버로 측정 시작 요청을 보낸다.

```http
POST {HARDWARE_MEASUREMENT_START_URL}
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "measurementSessionId": 123
}
```

하드웨어는 이 요청을 받으면 아래 값을 저장해야 한다.

- `measurementSessionId`: 이후 모든 측정 상태 변경과 AI 분석 결과 저장에 사용한다.
- `Authorization`: 백엔드 상태 변경 API를 호출할 때 그대로 사용한다.

프론트가 온습도 측정 준비 완료 상태를 보내면, 백엔드는 하드웨어 환경 측정 API에 작업을 요청한다.

```http
POST {HARDWARE_ENVIRONMENT_MEASUREMENT_URL}
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "measurementSessionId": 123
}
```

프론트가 압력 측정 준비 완료 상태를 보내면, 백엔드는 하드웨어 압력 측정 API에 작업을 요청한다.

```http
POST {HARDWARE_PRESSURE_MEASUREMENT_URL}
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "measurementSessionId": 123
}
```

## 2. 하드웨어가 호출하는 상태 변경 API

하드웨어는 측정 단계가 바뀔 때마다 아래 API를 호출한다.

```http
PATCH {BACKEND_BASE_URL}/api/measurement-sessions/{measurementSessionId}/status?status={STATUS}
Authorization: Bearer {accessToken}
```

예시:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=CAPTURING_PHOTO" \
  -H "Authorization: Bearer {accessToken}"
```

## 3. 성공 흐름

전체 성공 흐름은 아래 순서로 진행한다.

```text
1. 프론트가 백엔드에 측정 세션 생성 요청
2. 백엔드가 하드웨어에 measurementSessionId와 Authorization 전달
3. 백엔드가 프론트에 WAITING_FOR_PHOTO 소켓 메시지 발행
4. 프론트가 사용자의 사진 촬영 준비 완료를 확인하고 READY_FOR_PHOTO PATCH 호출
5. 사진 촬영이 시작되면 하드웨어가 CAPTURING_PHOTO PATCH 호출
6. 하드웨어가 발 사진 촬영
7. 하드웨어/AI가 촬영 사전검증 수행. 발이 ArUco 마커를 가리면 재촬영 흐름으로 분기
8. 사전검증 통과 시 하드웨어가 WAITING_FOR_ENVIRONMENT PATCH 호출
9. 검증된 사진의 AI 분석을 진행하고 결과를 백엔드에 저장. 이후 환경/압력 측정과 분석은 겹쳐 진행 가능
10. 프론트가 사용자에게 온습도 측정을 위해 기기 가까이 이동하라고 안내
11. 프론트가 사용자의 온습도 측정 준비 완료를 확인하고 READY_FOR_ENVIRONMENT PATCH 호출
12. 백엔드가 하드웨어 환경 측정 API로 온습도 측정 요청
13. 온습도 측정이 시작되면 하드웨어가 MEASURING_ENVIRONMENT PATCH 호출
14. 하드웨어가 온습도 측정값을 AI 서버로 전달하고, AI 서버 또는 연동 주체가 환경 분석 결과를 백엔드에 저장
15. 온습도 측정 단계가 끝나면 하드웨어가 WAITING_FOR_PRESSURE PATCH 호출
16. 프론트가 사용자에게 FSR 센서 판 조작과 재탑승을 안내
17. 프론트가 사용자의 압력 측정 준비 완료를 확인하고 READY_FOR_PRESSURE PATCH 호출
18. 백엔드가 하드웨어 압력 측정 API로 압력 측정 요청
19. 압력 측정이 시작되면 하드웨어가 MEASURING_PRESSURE PATCH 호출
20. 하드웨어가 압력 센서 데이터를 측정하고 AI 서버로 전달
21. 하드웨어 또는 AI 서버가 분석 결과를 백엔드에 저장
22. 필요한 분석 결과 저장이 끝나면 COMPLETED PATCH 호출
23. 백엔드가 프론트에 MEASUREMENT_COMPLETED 소켓 메시지 발행
```

## 4. 상태별 의미와 호출 주체

| Status | 의미 | 주 호출 주체 |
| --- | --- | --- |
| `WAITING_FOR_PHOTO` | 사진 촬영 준비 대기 | 백엔드 |
| `READY_FOR_PHOTO` | 사용자가 사진 촬영 준비를 완료함 | 프론트 |
| `CAPTURING_PHOTO` | 하드웨어가 사진 촬영을 시작함 | 하드웨어 |
| `WAITING_FOR_RECAPTURE` | 사용자의 발이 ArUco 마커를 가려 발 위치 조정이 필요함 | 하드웨어/AI 사전검증 연동 주체 |
| `READY_FOR_RECAPTURE` | 사용자가 발 위치를 조정하고 다시 촬영하기를 누름 | 프론트 |
| `WAITING_FOR_ENVIRONMENT` | 사진 촬영이 끝났고 온습도 측정을 위해 사용자가 기기 가까이 이동해야 함 | 하드웨어 |
| `READY_FOR_ENVIRONMENT` | 사용자가 온습도 측정 준비를 완료함 | 프론트 |
| `MEASURING_ENVIRONMENT` | 하드웨어가 온습도를 측정 중임 | 하드웨어 |
| `WAITING_FOR_PRESSURE` | 온습도 측정이 끝났고 압력 측정 준비가 필요함 | 하드웨어 |
| `READY_FOR_PRESSURE` | 사용자가 압력 측정 준비를 완료함 | 프론트 |
| `MEASURING_PRESSURE` | 하드웨어가 압력 측정을 시작함 | 하드웨어 |
| `COMPLETED` | 모든 필수 분석 결과 저장 완료 | 하드웨어 또는 AI 연동 주체 |
| `FAILED` | 측정 실패 | 하드웨어 또는 AI 연동 주체 |

`READY_FOR_PHOTO`, `READY_FOR_ENVIRONMENT`, `READY_FOR_PRESSURE`는 사용자가 앱에서 준비 완료 버튼을 눌렀다는 신호다. 백엔드는 이 상태를 받은 뒤 하드웨어 단계별 명령 API를 호출한다. 현재 하드웨어는 WebSocket을 구독하지 않으므로, WebSocket 메시지를 직접 수신하는 구조가 아니다.

## 5. 하드웨어 상태 변경 예시

사진 촬영 시작:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=CAPTURING_PHOTO" \
  -H "Authorization: Bearer {accessToken}"
```

사진 촬영 완료 후 온습도 측정 안내 요청:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=WAITING_FOR_ENVIRONMENT" \
  -H "Authorization: Bearer {accessToken}"
```

온습도 측정 시작:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=MEASURING_ENVIRONMENT" \
  -H "Authorization: Bearer {accessToken}"
```

온습도 측정 완료 후 압력 측정 준비 요청:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=WAITING_FOR_PRESSURE" \
  -H "Authorization: Bearer {accessToken}"
```

압력 측정 시작:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=MEASURING_PRESSURE" \
  -H "Authorization: Bearer {accessToken}"
```

측정 완료:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=COMPLETED&measurementDurationSec=180" \
  -H "Authorization: Bearer {accessToken}"
```

`measurementDurationSec`는 생략 가능하다. 생략하면 백엔드가 측정 세션 생성 시각부터 현재까지의 시간을 기준으로 계산한다.

## 6. 실패 처리

복구 불가능한 오류는 `FAILED` 상태를 백엔드에 전달한다. 발에 의한 ArUco 마커 가림은 아래 재촬영 절의 예외 흐름을 사용한다.

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=FAILED&failureReason=CAMERA_ERROR&failureDetail=Camera%20timeout" \
  -H "Authorization: Bearer {accessToken}"
```

백엔드는 아래 값을 `measurement_session`에 저장한다.

- `failure_reason`
- `failure_detail`

`failureReason`을 보내지 않으면 백엔드는 `UNKNOWN`으로 저장한다.

사용 가능한 실패 원인:

| failureReason | 의미 |
| --- | --- |
| `CAMERA_ERROR` | 카메라 촬영 실패 |
| `INVALID_CAPTURE_DATA` | 유효하지 않은 촬영 데이터 또는 ArUco 마커 가림 재촬영 횟수 소진 |
| `PRESSURE_SENSOR_ERROR` | FSR 압력 센서 측정 실패 |
| `AI_SERVER_ERROR` | AI 서버 요청 또는 분석 실패 |
| `HARDWARE_TIMEOUT` | 하드웨어 측정 시간 초과 |
| `NETWORK_ERROR` | 네트워크 통신 실패 |
| `USER_CANCELLED` | 사용자가 측정을 중단함 |
| `UNKNOWN` | 분류되지 않은 실패 |

## 7. 프론트로 전달되는 소켓 메시지

하드웨어가 상태 변경 API를 호출하면 백엔드는 프론트에 WebSocket 메시지를 발행한다.

구독 topic:

```text
/topic/measurements/{measurementSessionId}
```

실패 메시지 예시:

```json
{
  "eventType": "MEASUREMENT_FAILED",
  "measurementSessionId": 123,
  "status": "FAILED",
  "statusMessage": "측정 중 문제가 발생했습니다. 다시 시도해 주세요.",
  "failureReason": "CAMERA_ERROR",
  "failureDetail": "Camera timeout",
  "shouldDisconnect": true
}
```

완료 메시지 예시:

```json
{
  "eventType": "MEASUREMENT_COMPLETED",
  "measurementSessionId": 123,
  "status": "COMPLETED",
  "statusMessage": "분석이 완료되었습니다. 결과를 확인해 주세요.",
  "shouldDisconnect": true
}
```

`COMPLETED` 또는 `FAILED` 메시지는 `shouldDisconnect=true`로 전달된다. 프론트는 이 값을 받으면 해당 측정 세션 topic 구독을 해제하거나 WebSocket 연결을 종료하면 된다.

## 8. 완료 처리 주의사항

`COMPLETED` 요청은 강제 완료 명령이 아니라 내부 완료 플래그 검사 요청이다.

사진 수집/분석, 압력 수집/분석, 환경 분석, 필수 지표 리포트가 모두 준비되어야 완료된다. 일반 분석 진행 중에는 조건이 부족하면 현재 상태를 유지한다. 재촬영 대기/준비/촬영 중에는 상태 변경 API로 완료를 요청할 수 없고(409), 분석 결과 저장에 따른 자동 완료도 막는다.

## 9. 하드웨어 구현 체크리스트

- 백엔드 start 요청에서 받은 `measurementSessionId`를 저장한다.
- 백엔드 start 요청에서 받은 `Authorization` 헤더를 저장한다.
- 사진 촬영 시작 시 `CAPTURING_PHOTO`를 호출한다.
- 사진 촬영 완료 후 온습도 측정을 위해 사용자가 기기 가까이 이동해야 하면 `WAITING_FOR_ENVIRONMENT`를 호출한다.
- 온습도 측정 시작 시 `MEASURING_ENVIRONMENT`를 호출한다.
- 온습도 측정 완료 후 사용자가 압력 센서를 준비해야 하면 `WAITING_FOR_PRESSURE`를 호출한다.
- 압력 측정 시작 시 `MEASURING_PRESSURE`를 호출한다.
- AI 또는 백엔드 저장 API 호출이 실패하면 `FAILED`와 실패 원인을 호출한다.
- 필수 분석 결과 저장 후 `COMPLETED`를 호출한다.
- `FAILED` 이후에는 같은 세션으로 추가 측정을 진행하지 않는다.
- 네트워크 오류로 상태 변경 API 호출이 실패하면 같은 상태를 재시도할 수 있다.

## 10. 발에 의한 ArUco 마커 가림과 재촬영

### 적용 범위

`WAITING_FOR_RECAPTURE`는 **사용자의 발이 기준 ArUco 마커를 가려 발 위치를 조정해야 하는 경우**에만 사용한다. 백엔드는 사진을 직접 판별하지 않으므로 하드웨어/AI의 촬영 사전검증 연동 주체가 이 경우를 구분해 전달해야 한다.

카메라 고장, 통신 장애, 일반적인 촬영 데이터 오류, GPT 출력 검증 실패를 모두 재촬영으로 바꾸지 않는다. `FAILED + INVALID_CAPTURE_DATA` 요청도 최종 실패로 유지된다. 발에 의한 마커 가림일 때는 반드시 `FAILED` **대신** `WAITING_FOR_RECAPTURE`를 보내야 한다. 요청에 `failureReason`은 생략 가능하며, 함께 보내려면 `INVALID_CAPTURE_DATA`만 허용한다.

검증 실패한 사진은 AI 분석 대기열에 등록하거나 분석 결과로 저장하지 않는다. 마커 가림 검증은 `WAITING_FOR_ENVIRONMENT`로 진행하기 전에 끝내야 한다. 사전검증 통과 후에는 `WAITING_FOR_ENVIRONMENT` 상태 변경이 성공한 뒤 분석 결과를 저장한다. 재촬영 대기/준비/촬영 중 리포트 저장은 409로 거절된다.

이 기능은 **결과 저장 전 촬영 사전검증 실패**를 복구한다. 이미 분석 중이거나 일부 결과가 저장된 세션을 이전 촬영 단계로 되돌리는 기능이 아니며, 보고서 저장 API에 촬영 회차를 추가한 것은 아니다.

### 상태와 회차

```text
CAPTURING_PHOTO (photoCaptureAttempt=0: 최초 촬영)
  -> WAITING_FOR_RECAPTURE (발이 ArUco 마커를 가림, 소켓 유지)
  -> READY_FOR_RECAPTURE (사용자의 다시 촬영하기 버튼, 회차 +1)
  -> CAPTURING_PHOTO (새 회차로 촬영)
  -> WAITING_FOR_ENVIRONMENT (검증 통과)
```

최초 촬영 외에 재촬영을 **최대 3회** 허용하므로 총 촬영 기회는 4회다. 마커 가림 콜백이 아니라 `READY_FOR_RECAPTURE`로 실제 재촬영을 시작할 때 횟수를 증가시킨다. 3번째 재촬영에서도 마커 가림이 보고되면 백엔드가 `FAILED / INVALID_CAPTURE_DATA`로 처리한다.

재촬영은 동일한 `measurementSessionId`에서 사진 촬영 API만 다시 호출한다. 새 세션이나 촬영 전 온습도 측정을 다시 시작하지 않는다. 사진 수집/사진 분석/지표 리포트 완료 플래그를 초기화하고, 환경/압력 완료 플래그는 보존한다. 세션 행 잠금으로 중복 버튼 입력을 직렬화하며, 같은 준비 상태에서 하드웨어 요청을 중복 발송하지 않는다.

### 요청 계약

발에 의한 마커 가림을 확인한 하드웨어/AI 연동 주체:

```http
PATCH {BACKEND_BASE_URL}/api/measurement-sessions/79/status?status=WAITING_FOR_RECAPTURE&photoCaptureAttempt=0
Authorization: Bearer {accessToken}
```

`failureDetail`은 선택 사항이다. 생략하면 발 위치를 조정하라는 기본 안내를 사용한다. 전달하는 경우 쿼리 파라미터를 URL 인코딩해야 하며, 사용자에게 노출 가능한 안내만 보낸다.

프론트의 다시 촬영하기 버튼:

```http
PATCH {BACKEND_BASE_URL}/api/measurement-sessions/79/status?status=READY_FOR_RECAPTURE
Authorization: Bearer {accessToken}
```

프론트도 현재 소켓 메시지의 `photoCaptureAttempt`를 쿼리로 함께 보내는 것을 권장한다. 이전 회차의 버튼 요청이 늦게 도착해 새 회차를 시작하는 일을 차단할 수 있다. 버튼 요청에는 다음 회차가 아니라 **현재 회차**를 보낸다.

백엔드는 커밋 후 `hardware.measurement.photo-capture-url`로 다음 요청을 보낸다. `/measurement/start`, 환경 측정, 압력 측정 API는 재호출하지 않는다.

```http
POST {HARDWARE_PHOTO_CAPTURE_URL}
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "measurementSessionId": 79,
  "photoCaptureAttempt": 1
}
```

하드웨어는 받은 회차를 저장하고, 해당 촬영의 상태 콜백에 그대로 포함해야 한다.

```http
PATCH {BACKEND_BASE_URL}/api/measurement-sessions/79/status?status=CAPTURING_PHOTO&photoCaptureAttempt=1
```

재촬영에서도 발이 마커를 가린 경우:

```http
PATCH {BACKEND_BASE_URL}/api/measurement-sessions/79/status?status=WAITING_FOR_RECAPTURE&photoCaptureAttempt=1
```

검증 통과 시:

```http
PATCH {BACKEND_BASE_URL}/api/measurement-sessions/79/status?status=WAITING_FOR_ENVIRONMENT&photoCaptureAttempt=1
```

최초 촬영 요청 본문은 기존처럼 `measurementSessionId`만 보내며 최초 회차 0은 콜백에서 생략 가능하다. 재촬영(1~3회)부터는 사진 시작/성공/재촬영 대기 콜백 및 `FAILED`의 `CAMERA_ERROR`/`INVALID_CAPTURE_DATA` 콜백에 회차가 필수다. AI가 상태 콜백을 대신 보내는 경우에도 하드웨어가 회차를 전달해야 한다. 회차 누락/불일치는 409 `MEASUREMENT4009`, 허용되지 않는 재촬영 상태 전이는 409 `MEASUREMENT4008`이다.

### 소켓과 프론트

기존 `/topic/measurements/{id}` 및 `/topic/users/{userId}/measurements`로 DB 커밋 후 전송한다. 기존 `statusMessage`를 유지하며 같은 내용의 `message` 필드도 제공한다.

```json
{
  "eventType": "MEASUREMENT_STATUS_CHANGED",
  "measurementSessionId": 79,
  "status": "WAITING_FOR_RECAPTURE",
  "statusMessage": "사진을 다시 촬영해야 합니다.",
  "message": "사진을 다시 촬영해야 합니다.",
  "detail": "발이 기준 ArUco 마커를 가리고 있습니다. 마커가 모두 보이도록 발 위치를 조정한 뒤 다시 촬영해 주세요.",
  "photoCaptureAttempt": 0,
  "remainingPhotoRecaptures": 3,
  "shouldDisconnect": false
}
```

프론트는 `detail`과 다시 촬영하기 버튼을 표시하고 연결을 유지한다. 재촬영 대기에는 최종 실패 필드가 노출되지 않는다. 세 번째 재촬영도 실패하면 `eventType=MEASUREMENT_FAILED`, `status=FAILED`, `failureReason=INVALID_CAPTURE_DATA`, `shouldDisconnect=true`로 바뀐다. `remainingPhotoRecaptures`는 앞으로 **시작할 수 있는** 횟수이므로 세 번째 재촬영 진행 중에도 0이며, 그 자체로 실패를 뜻하지 않는다.

## 11. 배포 확인

- 백엔드뿐 아니라 하드웨어/AI의 마커 가림 분기와 `photoCaptureAttempt` 전달도 반영해야 한다. 기존처럼 `FAILED`를 보내는 외부 코드를 그대로 두면 재촬영 흐름이 시작되지 않는다.
- `measurement_session.photo_recapture_count` (`INT NOT NULL DEFAULT 0`)와 `recapture_detail` (`TEXT NULL`)이 필요하다. 기존 세션의 회차는 0으로 초기화한다.
- `status` 컬럼이 MySQL ENUM이면 기존 값은 유지한 채 `WAITING_FOR_RECAPTURE`, `READY_FOR_RECAPTURE`를 허용해야 한다. `failure_reason`에도 `INVALID_CAPTURE_DATA`를 허용해야 한다.
- 운영 스키마를 먼저 확인하고 마이그레이션 또는 Hibernate 스키마 갱신 결과를 확인한다. 이 변경으로 운영 DB에 직접 SQL을 실행하지는 않는다.
- 검증 실패 사진이 분석 대기열/저장 단계로 넘어가지 않는지, 재촬영 3회 실패와 정상 복구를 실제 기기에서 확인한다.
