# Hardware Measurement Flow Guide

이 문서는 FeetFit 하드웨어가 백엔드와 연동할 때 따라야 하는 측정 진행 흐름을 정리한다.

## 핵심 원칙

- 하드웨어는 WebSocket을 구독하지 않는다.
- 하드웨어는 백엔드에서 전달받은 `measurementSessionId`와 `Authorization` 토큰을 저장해두고, 이후 백엔드 API 호출에 그대로 사용한다.
- 프론트와 백엔드는 WebSocket STOMP로 측정 상태를 주고받는다.
- 하드웨어는 측정 단계가 바뀔 때마다 백엔드의 상태 변경 API를 호출한다.
- 백엔드는 상태 변경을 저장한 뒤 프론트가 구독 중인 `/topic/measurements/{measurementSessionId}`로 상태 메시지를 발행한다.
- 측정 중 실패하면 하드웨어는 `FAILED` 상태와 실패 원인을 백엔드로 전달한다.

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
7. 하드웨어가 사진을 AI 서버로 전달
8. AI 서버가 무지외반, 무좀 분석 결과를 백엔드에 저장
9. 사진 측정 단계가 끝나면 하드웨어가 WAITING_FOR_PRESSURE PATCH 호출
10. 프론트가 사용자에게 FSR 센서 판 조작과 재탑승을 안내
11. 프론트가 사용자의 압력 측정 준비 완료를 확인하고 READY_FOR_PRESSURE PATCH 호출
12. 압력 측정이 시작되면 하드웨어가 MEASURING_PRESSURE PATCH 호출
13. 하드웨어가 압력, 온습도, 냄새 등 센서 데이터 측정
14. 하드웨어 또는 AI 서버가 분석 결과를 백엔드에 저장
15. 필요한 분석 결과 저장이 끝나면 COMPLETED PATCH 호출
16. 백엔드가 프론트에 MEASUREMENT_COMPLETED 소켓 메시지 발행
```

## 4. 상태별 의미와 호출 주체

| Status | 의미 | 주 호출 주체 |
| --- | --- | --- |
| `WAITING_FOR_PHOTO` | 사진 촬영 준비 대기 | 백엔드 |
| `READY_FOR_PHOTO` | 사용자가 사진 촬영 준비를 완료함 | 프론트 |
| `CAPTURING_PHOTO` | 하드웨어가 사진 촬영을 시작함 | 하드웨어 |
| `WAITING_FOR_PRESSURE` | 사진 촬영이 끝났고 압력 측정 준비가 필요함 | 하드웨어 |
| `READY_FOR_PRESSURE` | 사용자가 압력 측정 준비를 완료함 | 프론트 |
| `MEASURING_PRESSURE` | 하드웨어가 압력 측정을 시작함 | 하드웨어 |
| `COMPLETED` | 모든 필수 분석 결과 저장 완료 | 하드웨어 또는 AI 연동 주체 |
| `FAILED` | 측정 실패 | 하드웨어 또는 AI 연동 주체 |

`READY_FOR_PHOTO`, `READY_FOR_PRESSURE`는 사용자가 앱에서 준비 완료 버튼을 눌렀다는 신호다. 하드웨어가 이 준비 완료 상태를 직접 알아야 한다면, 별도의 백엔드 -> 하드웨어 단계별 명령 API가 필요하다. 현재 하드웨어는 WebSocket을 구독하지 않으므로, WebSocket 메시지를 직접 수신하는 구조가 아니다.

## 5. 하드웨어 상태 변경 예시

사진 촬영 시작:

```bash
curl -X PATCH \
  "http://34.209.169.111/api/measurement-sessions/123/status?status=CAPTURING_PHOTO" \
  -H "Authorization: Bearer {accessToken}"
```

사진 촬영 완료 후 압력 측정 준비 요청:

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

측정 중 어느 단계에서든 실패하면 하드웨어는 `FAILED` 상태를 백엔드에 전달한다.

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

`COMPLETED` 상태 변경은 무지외반 분석 결과와 무좀 분석 결과가 백엔드에 저장된 뒤에만 성공한다.

필수 분석 결과가 아직 저장되지 않았는데 `COMPLETED`를 호출하면 백엔드는 아래 오류를 반환한다.

```json
{
  "isSuccess": false,
  "code": "MEASUREMENT4005",
  "message": "무지외반 또는 무좀 분석 결과가 아직 저장되지 않았습니다.",
  "result": null
}
```

이 경우 하드웨어 또는 AI 연동 주체는 분석 결과 저장 완료 후 `COMPLETED` 요청을 다시 보내야 한다.

## 9. 하드웨어 구현 체크리스트

- 백엔드 start 요청에서 받은 `measurementSessionId`를 저장한다.
- 백엔드 start 요청에서 받은 `Authorization` 헤더를 저장한다.
- 사진 촬영 시작 시 `CAPTURING_PHOTO`를 호출한다.
- 사진 촬영 완료 후 사용자가 압력 센서를 준비해야 하면 `WAITING_FOR_PRESSURE`를 호출한다.
- 압력 측정 시작 시 `MEASURING_PRESSURE`를 호출한다.
- AI 또는 백엔드 저장 API 호출이 실패하면 `FAILED`와 실패 원인을 호출한다.
- 필수 분석 결과 저장 후 `COMPLETED`를 호출한다.
- `FAILED` 이후에는 같은 세션으로 추가 측정을 진행하지 않는다.
- 네트워크 오류로 상태 변경 API 호출이 실패하면 같은 상태를 재시도할 수 있다.
