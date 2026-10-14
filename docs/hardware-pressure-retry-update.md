# 하드웨어 압력 재측정 수정 방향

## 현재 문제

압력 측정 서버가 백엔드 상태 변경 API를 호출할 때 `pressureMeasurementAttempt`를 전달하지 않아 백엔드가 `409 CONFLICT`를 반환한다.

문제가 된 요청:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status?status=MEASURING_PRESSURE
PATCH /api/measurement-sessions/{measurementSessionId}/status?status=ANALYZING
```

백엔드는 압력 재측정이 시작된 세션에서는 압력 관련 콜백에 현재 압력 측정 회차를 요구한다.

정상 요청:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status?status=MEASURING_PRESSURE&pressureMeasurementAttempt={attempt}
PATCH /api/measurement-sessions/{measurementSessionId}/status?status=ANALYZING&pressureMeasurementAttempt={attempt}
```

## 하드웨어가 받아야 하는 값

백엔드는 압력 측정 요청 시 하드웨어에 아래 body를 보낸다.

최초 압력 측정:

```json
{
  "measurementSessionId": 4,
  "pressureMeasurementAttempt": 0
}
```

압력 재측정:

```json
{
  "measurementSessionId": 4,
  "pressureMeasurementAttempt": 1
}
```

`pressureMeasurementAttempt` 값은 하드웨어가 새로 계산하지 말고, 백엔드가 보낸 값을 그대로 사용해야 한다.

## 하드웨어 수정 위치

압력 측정 시작 엔드포인트:

```text
POST /measurement/pressure/start
```

요청 DTO 또는 request body 파싱 코드에 `pressureMeasurementAttempt`를 추가한다.

예시:

```python
class PressureStartRequest(BaseModel):
    measurementSessionId: int
    pressureMeasurementAttempt: int = 0
```

## 상태 변경 콜백 수정

하드웨어가 백엔드로 압력 상태를 변경할 때 모든 압력 관련 상태에 같은 `pressureMeasurementAttempt`를 붙인다.

압력 측정 시작:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status
?status=MEASURING_PRESSURE
&pressureMeasurementAttempt={pressureMeasurementAttempt}
```

압력 측정 완료:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status
?status=ANALYZING
&pressureMeasurementAttempt={pressureMeasurementAttempt}
```

압력값 부족으로 재측정 필요:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status
?status=WAITING_FOR_PRESSURE_RETRY
&failureReason=INVALID_PRESSURE_DATA
&failureDetail=압력값이 부족합니다.
&pressureMeasurementAttempt={pressureMeasurementAttempt}
```

압력 센서 최종 실패:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status
?status=FAILED
&failureReason=PRESSURE_SENSOR_ERROR
&failureDetail=압력 센서 측정에 실패했습니다.
&pressureMeasurementAttempt={pressureMeasurementAttempt}
```

## 코드 예시

```python
def pressure_start(request: PressureStartRequest):
    session_id = request.measurementSessionId
    attempt = request.pressureMeasurementAttempt

    patch_status(
        session_id,
        "MEASURING_PRESSURE",
        pressure_measurement_attempt=attempt,
    )

    # 압력 측정 수행

    patch_status(
        session_id,
        "ANALYZING",
        pressure_measurement_attempt=attempt,
    )
```

`patch_status()`가 query parameter를 만들 수 있어야 한다.

```python
def patch_status(
    session_id: int,
    status: str,
    pressure_measurement_attempt: int | None = None,
    failure_reason: str | None = None,
    failure_detail: str | None = None,
):
    params = {"status": status}

    if pressure_measurement_attempt is not None:
        params["pressureMeasurementAttempt"] = pressure_measurement_attempt
    if failure_reason is not None:
        params["failureReason"] = failure_reason
    if failure_detail is not None:
        params["failureDetail"] = failure_detail

    response = requests.patch(
        f"{BACKEND_URL}/api/measurement-sessions/{session_id}/status",
        params=params,
    )
    response.raise_for_status()
    return response
```

## 정상 흐름

최초 압력 측정:

```text
백엔드 -> 하드웨어
POST /measurement/pressure/start
body: { measurementSessionId: 4, pressureMeasurementAttempt: 0 }

하드웨어 -> 백엔드
PATCH status=MEASURING_PRESSURE&pressureMeasurementAttempt=0

하드웨어 -> 백엔드
PATCH status=ANALYZING&pressureMeasurementAttempt=0
```

압력 재측정:

```text
백엔드 -> 하드웨어
POST /measurement/pressure/start
body: { measurementSessionId: 4, pressureMeasurementAttempt: 1 }

하드웨어 -> 백엔드
PATCH status=MEASURING_PRESSURE&pressureMeasurementAttempt=1

하드웨어 -> 백엔드
PATCH status=ANALYZING&pressureMeasurementAttempt=1
```

## 주의 사항

- 재측정 회차는 백엔드가 관리한다.
- 하드웨어는 `pressureMeasurementAttempt`를 임의로 증가시키면 안 된다.
- 하드웨어는 백엔드가 보낸 값을 압력 시작, 성공, 재측정 요청, 실패 콜백에 그대로 전달해야 한다.
- 압력 재측정 중 `pressureMeasurementAttempt`가 누락되면 백엔드는 `409 MEASUREMENT4009` 또는 `409 MEASUREMENT4008`을 반환할 수 있다.
