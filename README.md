# Assignment 1 - Speech to Text (Java Spring Boot)

## ⚠️ One thing to fix before you submit
The assignment brief you gave me says the extra endpoints (uptime / stats /
graceful shutdown) are "specified in YAML", but that YAML block itself
wasn't in the text - only a placeholder line. I built:

- `GET  /api/v1/uptime`
- `GET  /api/v1/stats`
- `POST /api/v1/admin/shutdown` (this exact path *is* named in the brief, under Advanced Topics)

with response fields I judged reasonable. **Pull the actual YAML from the
subject site / Assignment 1 launch slides and check my paths, field names,
and status codes against it** (`AdminController.java`) - TITAN grades these
by exact machine testing, so a mismatched field name will fail checks even
if the logic is fine. Also confirm the exact path/field name expected for
the audio upload endpoint (I used `POST /api/v1/transcribe`, form field
`audio`) against anything TITAN's launch slides specify.

## What's here
- `TranscriptionController` - `POST /api/v1/transcribe`, multipart `audio` field, calls OpenAI, returns `{ "text": "..." }`.
- `AdminController` - uptime/stats/shutdown.
- `OpenAiSttService` - non-blocking call to OpenAI via `WebClient`.
- `AppStatsService` - lock-free counters (`LongAdder`/`AtomicLong`) for uptime/stats, safe under concurrent load.
- `OpenAiProperties` - reads `OPENAI_API_KEY` from the environment only; never logged, hard-coded, or returned to a client.
- `static/` - the recording page (vanilla JS, `MediaRecorder` + `fetch`).

## Why it handles >200 concurrent requests
Every controller method that talks to OpenAI returns
`CompletableFuture<ResponseEntity<?>>`. Spring MVC frees the Tomcat request
thread as soon as the method returns and only re-attaches a thread to write
the response once the `WebClient` call (built on Reactor Netty, non-blocking)
completes. So the Tomcat thread pool (`server.tomcat.threads.max=300` in
`application.properties`) is never sized to match concurrent *in-flight
OpenAI calls* - only concurrent *reads of the request body / writes of the
response*, which are fast. `AppStatsServiceConcurrencyTest` fires 250
concurrent updates at the shared counters to catch any race condition there.

## Config across local vs TITAN
`application.properties` holds shared config. `application-local.properties`
and `application-titan.properties` hold profile-specific overrides, selected
via `SPRING_PROFILES_ACTIVE` (defaults to `local`). Nothing about the API key
lives in any properties file - it's read once at startup straight from the
`OPENAI_API_KEY` environment variable (`OpenAiProperties`).

## Running locally
```bash
export OPENAI_API_KEY=sk-...        # PowerShell: $env:OPENAI_API_KEY="sk-..."
mvn spring-boot:run
# open http://localhost:8080/
```

## Testing
```bash
mvn test
```

## Building the fat JAR for hand-in
```bash
mvn clean package
java -jar target/stt-assignment.jar
```
This produces a single executable JAR via `spring-boot-maven-plugin`
(configured in `pom.xml`), matching the packaging requirement.

## Still worth doing before submission
- Swap in the real YAML-specified paths/fields once you have them (see the warning above).
- Add a `@SpringBootTest` that actually hits `/api/v1/transcribe` end-to-end against a stubbed STT service (for the High Distinction band on the REST API criterion) - currently only unit-level tests are included.
- Add the >200-concurrent-HTTP-request load test against a running server (e.g. with `RestTemplate`/`WebTestClient` in a loop, or a small script) for the Concurrency HD band; the current test proves the *counters* are race-free, not the full HTTP path under load.
- Decide how you want to handle mic permission failures / unsupported browsers in the UI beyond the current basic messaging, if you want to push for the Frontend HD band (accessibility, chunked/compressed upload).
