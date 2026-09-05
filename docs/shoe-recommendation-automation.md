# Shoe recommendation automation configuration

When a measurement session transitions to COMPLETED, FeetFit_Server publishes one
after-commit event and runs a single asynchronous workflow. The workflow first
finishes the foot-type-text generation/save attempt and only then invokes
Feetfit_AI for every shoe in the Server database. This ordering keeps the paged
recommendation context stable while `DailyFootAnalysis.typeText` is updated.
Foot-type-text failure does not block the recommendation step. Measurement
completion is not rolled back when either AI request fails; a failed shoe batch
marks the session-scoped SHOE_RECOMMENDATION_RUN as FAILED.

The ordered completion workflows and explicit shoe-batch retries use a dedicated
single-worker executor so two users completing measurements at the same time do
not invoke the GPU batch concurrently from one Server instance. When a detail
response is missing summaries, Server reads the recommendation context, calls
the AI direct-generation endpoint synchronously, saves the returned summaries,
and performs a fresh read before responding. This request path does not use the
batch executor and its AI wait is bounded by the dedicated summary timeout.

The application YAML remains local and ignored. Configure deployments with
environment variables:

| Environment variable | Spring property | Required | Purpose |
| --- | --- | --- | --- |
| INTERNAL_API_KEY | internal.api-key | yes | Shared Server/AI internal key; deployment YAML may map this environment secret to the canonical Spring property; never log or commit it |
| AI_SHOE_RECOMMENDATION_URL | ai.shoe-recommendation.url | yes when enabled | Absolute HTTP(S) batch endpoint, normally `/api/reports/shoe-recommendations` |
| AI_SHOE_RECOMMENDATION_SUMMARY_URL | ai.shoe-recommendation.summary-url | yes when enabled | Absolute HTTP(S) on-demand Ollama endpoint, normally `/api/shoes/summaries` |
| AI_FOOT_TYPE_TEXT_URL | ai.foot-type-text.url | no | Absolute HTTP(S) foot-type-text endpoint. When omitted, Server derives the sibling `/api/reports/foot-type-text` endpoint from the batch URL for backward compatibility |
| AI_SHOE_RECOMMENDATION_TIMEOUT_SECONDS | ai.shoe-recommendation.timeout-seconds | no | Positive outer HTTP timeout; defaults to 1200 seconds |
| AI_SHOE_RECOMMENDATION_SUMMARY_TIMEOUT_SECONDS | ai.shoe-recommendation.summary-timeout-seconds | no | Detail summary AI timeout from 1 to 10 seconds; defaults to 10 seconds |
| AI_SHOE_RECOMMENDATION_ENABLED | ai.shoe-recommendation.enabled | no | Enables batch and summary triggers; defaults to true |

The internal API interceptor and automation client both read the canonical
`internal.api-key` Spring property. Deployment YAML can resolve it from
`INTERNAL_API_KEY`, while a local ignored YAML may provide a development
fallback. Server startup fails fast when the resolved value is blank. When
automation is enabled it also fails fast without either required shoe AI URL,
when any configured URL (including the optional foot-type-text URL) is not
absolute HTTP(S), or when a timeout is outside its accepted range. Tests
explicitly disable external automation.

The Server batch timeout is intentionally longer than the AI-to-Server callback
timeout so paged context loading and BGE work do not consume the entire caller
budget before callbacks finish. Detail generation instead uses its separate,
at-most-10-second timeout before the save and fresh read complete.

There is no blind HTTP retry. A timeout can make the remote commit outcome
uncertain, so failures are recorded and must be retriggered deliberately. The
session+shoe unique constraint and recommendation upsert make a deliberate replay
idempotent. A RUNNING or COMPLETED run cannot be claimed twice; a FAILED or
PENDING run can be claimed again for the same measurement session through:

`POST /api/internal/shoe-analysis/recommendation-runs/{measurementSessionId}/retry-automatic`

Queue rejection before the async worker starts is logged and leaves no RUNNING
claim; the same explicit retry endpoint can safely create and claim the run. A
RUNNING run is never taken over based only on elapsed time because the original
worker may still be alive. If a process dies while RUNNING, an operator must first
verify that no worker is active and that the remote commit outcome is known, mark
the run FAILED through the existing `/fail` endpoint, and then explicitly retry.
This avoids stale-attempt fencing races and blind retries after commit-unknown
timeouts.

Bearer tokens and the internal key must not cross a public plaintext network.
Use HTTPS for non-loopback endpoints or a private trusted network path.
