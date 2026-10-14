# AI 서버 압력 재측정 수정 방향

## 현재 문제

압력 분석 요청은 큐에 정상 등록되지만, AI 서버 또는 압력 처리 서버가 백엔드 상태 변경 API를 호출할 때 `pressureMeasurementAttempt`를 전달하지 않아 백엔드가 `409 CONFLICT`를 반환한다.

로그에서 확인된 문제 요청:

```text
PATCH /api/measurement-sessions/4/status?status=MEASURING_PRESSURE
PATCH /api/measurement-sessions/4/status?status=ANALYZING
```

정상 요청:

```text
PATCH /api/measurement-sessions/4/status?status=MEASURING_PRESSURE&pressureMeasurementAttempt=1
PATCH /api/measurement-sessions/4/status?status=ANALYZING&pressureMeasurementAttempt=1
```

## AI 서버가 유지해야 하는 값

압력 분석 요청 payload에 `pressureMeasurementAttempt`를 포함하고, 큐 작업 context에도 저장해야 한다.

예시:

```json
{
  "measurementSessionId": 4,
  "pressureMeasurementAttempt": 1,
  "leftPressureValues": [100, 120],
  "rightPressureValues": [90, 110]
}
```

기본값은 최초 압력 측정용 `0`이다.

```python
class PressureAnalysisRequest(BaseModel):
    measurementSessionId: int
    pressureMeasurementAttempt: int = 0
```

## 백엔드 상태 변경 호출 수정

AI 서버가 백엔드에 압력 관련 상태를 직접 PATCH하는 경우, 모든 압력 상태 콜백에 `pressureMeasurementAttempt`를 붙여야 한다.

압력 측정 시작:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status
?status=MEASURING_PRESSURE
&pressureMeasurementAttempt={pressureMeasurementAttempt}
```

압력 분석 시작 또는 측정 완료:

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

압력 분석 또는 저장 실패:

```text
PATCH /api/measurement-sessions/{measurementSessionId}/status
?status=FAILED
&failureReason=PRESSURE_SENSOR_ERROR
&failureDetail=압력 분석 또는 저장에 실패했습니다.
&pressureMeasurementAttempt={pressureMeasurementAttempt}
```

## backend_client.py 수정 예시

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
    if failure_reason:
        params["failureReason"] = failure_reason
    if failure_detail:
        params["failureDetail"] = failure_detail

    response = requests.patch(
        f"{BACKEND_URL}/api/measurement-sessions/{session_id}/status",
        params=params,
    )
    response.raise_for_status()
    return response
```

## pressure.py 수정 예시

현재 문제가 난 위치:

```text
/home/chaeni/feetfit-api/routers/pressure.py:54
/home/chaeni/feetfit-api/routers/pressure.py:91
```

수정 예시:

```python
def pressure_start(request: PressureStartRequest):
    session_id = request.measurementSessionId
    attempt = request.pressureMeasurementAttempt

    patch_status(
        session_id,
        "MEASURING_PRESSURE",
        pressure_measurement_attempt=attempt,
    )

    enqueue_pressure_job(
        measurement_session_id=session_id,
        pressure_measurement_attempt=attempt,
        # pressure values/images...
    )

    return {
        "accepted": True,
        "status": "QUEUED",
        "measurementSessionId": session_id,
        "pressureMeasurementAttempt": attempt,
    }
```

압력 분석 작업 내부:

```python
def run_pressure_job(job: PressureJob):
    attempt = job.pressure_measurement_attempt

    try:
        # 압력 히트맵 생성 및 결과 저장

        patch_status(
            job.measurement_session_id,
            "ANALYZING",
            pressure_measurement_attempt=attempt,
        )
    except RecoverablePressureError as error:
        patch_status(
            job.measurement_session_id,
            "WAITING_FOR_PRESSURE_RETRY",
            pressure_measurement_attempt=attempt,
            failure_reason="INVALID_PRESSURE_DATA",
            failure_detail=str(error),
        )
    except Exception as error:
        patch_status(
            job.measurement_session_id,
            "FAILED",
            pressure_measurement_attempt=attempt,
            failure_reason="PRESSURE_SENSOR_ERROR",
            failure_detail=str(error),
        )
        raise
```

## 리포트 저장 API 주의 사항

백엔드 리포트 저장 API 자체에는 `pressureMeasurementAttempt`를 붙이지 않는다.

예시:

```text
POST /api/reports/daily-foot-analysis/pressure
POST /api/reports/daily-foot-analysis/pressure-heatmap-image
POST /api/reports/summary
```

`pressureMeasurementAttempt`는 측정 세션 상태 변경 API에만 필요하다.

## 정상 흐름

최초 압력 측정:

```text
AI 서버가 받은 값: pressureMeasurementAttempt=0
PATCH MEASURING_PRESSURE&pressureMeasurementAttempt=0
압력 분석/저장
PATCH ANALYZING&pressureMeasurementAttempt=0
```

압력 재측정:

```text
AI 서버가 받은 값: pressureMeasurementAttempt=1
PATCH MEASURING_PRESSURE&pressureMeasurementAttempt=1
압력 분석/저장
PATCH ANALYZING&pressureMeasurementAttempt=1
```

## 확인 방법

백엔드 DB에서 현재 회차를 확인한다.

```sql
SELECT
  id,
  status,
  pressure_retry_count,
  pressure_retry_detail,
  updated_at
FROM measurement_session
WHERE id = 4;
```

판단 기준:

- `pressure_retry_count = 0`: AI 서버는 `pressureMeasurementAttempt=0` 전달
- `pressure_retry_count = 1`: AI 서버는 `pressureMeasurementAttempt=1` 전달
- `status = READY_FOR_PRESSURE_RETRY`: 다음 콜백은 `MEASURING_PRESSURE&pressureMeasurementAttempt=1`
- `status = MEASURING_PRESSURE`: 다음 콜백은 `ANALYZING&pressureMeasurementAttempt=1`

## 주의 사항

- AI 서버는 `pressureMeasurementAttempt`를 임의로 증가시키면 안 된다.
- 하드웨어 또는 백엔드에서 전달받은 값을 큐 작업까지 그대로 유지해야 한다.
- 압력 재측정 중 `pressureMeasurementAttempt`가 누락되면 백엔드는 오래된 압력 콜백으로 보고 `409`를 반환한다.
- 사진 재촬영용 `photoCaptureAttempt`와 압력 재측정용 `pressureMeasurementAttempt`는 별개 필드다.
