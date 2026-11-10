
# AquaOps - Detection Service

**Owner:** E3 (silent sensor detection: E1)

**Project:** AquaOps - Water Distribution Monitoring System

**Technology:** Java 21, Spring Boot 4.1.1, Apache Kafka

## 1. Overview

The Detection Service is a Spring Boot microservice responsible
for analyzing sensor readings from the AquaOps water
distribution monitoring platform.

It consumes sensor readings from Apache Kafka, applies
detection rules, and publishes anomalies when abnormal
conditions are identified.

The service currently supports:

- Pump fault detection (SCRUM-33 / FR-11)
- Probable leak detection (SCRUM-31 / FR-09)
- Anomaly deduplication and recovery (SCRUM-36 / FR-14)
- Silent sensor detection (SCRUM-32 / FR-10)

The anomalies are published to Kafka for consumption by
downstream services, including the Incident Service (E4).

## 2. Architecture

Data processing flow:

    Sensor Simulator
          |
          v
    Kafka: sensor-readings
          |
          v
    SensorReadingListener
          |
          +-----------------------------+
          |                             |
          v                             v
    DetectionEngine               SilenceDetector
          |                       (scheduled check)
          +--------------------+        |
          |                    |        |
          v                    v        |
    PumpFaultRule           LeakRule    |
                                 |      |
                                 v      |
                         PressureWindowTracker
          |                    |        |
          +--------------------+        |
          |                             |
          v                             |
    AnomalyDeduplicator                 |
          |                             |
          v                             v
    AnomalyPublisher <------------------+
          |
          v
    Kafka: anomaly-detected
          |
          v
    Incident Service (E4)

### Main components

- SensorReadingListener: consumes sensor events from Kafka.
- DetectionEngine: executes the configured detection rules.
- PumpFaultRule: detects pump faults.
- PressureWindowTracker: tracks low pressure over time.
- LeakRule: detects probable leaks.
- AnomalyDeduplicator: suppresses repeated anomalies.
- SilenceDetector: detects sensors that stopped sending readings.
- AnomalyPublisher: publishes anomalies to Kafka.
- DetectionConfig: configures the Spring beans and rules.
- SchedulingConfig: enables Spring scheduling for the silence check.

## 3. SCRUM-33 - Pump Fault Detection (FR-11)

### Objective

Automatically detect pump failures using sensor readings
received from Kafka.

### Detection rule

A pump fault is detected when:

    type = PUMP_STATE
    pumpState = FAULT

The service generates an anomaly of type:

    PUMP_FAULT

A reading with:

    pumpState = NORMAL

does not generate a pump fault anomaly.

### Example input

Kafka topic: sensor-readings

```json
{
  "eventVersion": "1",
  "sensorId": 15,
  "zoneId": 3,
  "type": "PUMP_STATE",
  "pumpState": "FAULT",
  "measuredAt": "2026-11-02T15:00:00Z"
}
```

### Expected output

Kafka topic: anomaly-detected

```json
{
  "eventVersion": "1",
  "anomalyId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "PUMP_FAULT",
  "zoneId": 3,
  "sensorId": 15,
  "measuredAt": "2026-11-02T15:00:00Z",
  "detectedAt": "2026-11-02T15:00:01Z"
}
```

The anomalyId and detectedAt values are generated at
runtime. The example above illustrates the event structure.

### Implementation

- PumpFaultRule evaluates pump state readings.
- DetectionEngine processes the rules.
- SensorReadingListener receives Kafka messages.
- AnomalyPublisher sends anomalies to Kafka.
- DetectionConfig registers the required Spring beans.

### Testing

Implemented tests include:

- PumpFaultRuleTest
- PumpFaultScenarioTest
- DetectionEngineTest
- DetectionKafkaIntegrationTest

The CSV test dataset is located at:

    src/test/resources/pump-scenarios.csv

It covers pump faults, normal pump states and
non-pump sensor readings.

## 4. SCRUM-31 - Probable Leak Detection (FR-09)

### Objective

Detect probable water leaks by combining pressure and flow
measurements from the same zone.

### Detection conditions

A probable leak is detected when:

1. Pressure remains below the configured minimum threshold
   for at least N seconds.
2. Flow exceeds the configured maximum threshold.
3. Pressure and flow readings belong to the same zone.
4. The latest pressure and flow measurements are no more
   than 30 seconds apart.

All conditions must be satisfied.

The service generates:

    LEAK_SUSPECTED

### Default thresholds

| Parameter | Default value | Description |
|-----------|---------------|-------------|
| LEAK_MIN_PRESSURE | 2.0 | Minimum pressure threshold |
| LEAK_MAX_FLOW | 40.0 | Maximum flow threshold |
| LEAK_DURATION_SECONDS | 10 | Required low-pressure duration |

These are provisional testing values and must be validated
against the AquaOps sensor simulator and operational needs.

The maximum timestamp difference between pressure and
flow readings is currently fixed at 30 seconds in LeakRule.

### Example detection scenario

The following readings are received for zone 3.

First reading, at 10:00:00:

    PRESSURE = 1.5 bar

Second reading, at 10:00:02:

    FLOW = 45.0 m3/h

Third reading, at 10:00:10:

    PRESSURE = 1.4 bar

The pressure has remained below 2.0 for 10 seconds,
and the flow exceeds 40.0.

Expected anomaly:

    LEAK_SUSPECTED

### Implementation

PressureWindowTracker:

- Tracks the start of low-pressure periods per zone.
- Resets the period when pressure returns to normal.
- Ignores older or repeated pressure timestamps.
- Separates the state of different zones.

LeakRule:

- Evaluates pressure and flow measurements.
- Maintains the latest accepted measurements per zone.
- Combines the two detection conditions.
- Checks timestamp proximity.
- Generates LEAK_SUSPECTED anomalies.
- Detects recovery when pressure or flow returns to normal.

### Tests

Implemented tests include:

- PressureWindowTrackerTest
- LeakRuleTest
- LeakKafkaIntegrationTest

Test scenarios cover:

- Low pressure with high flow
- Low pressure only
- High flow only
- Normal pressure recovery
- Different zones
- Stale pressure readings
- Stale flow readings
- Kafka anomaly publication

## 5. SCRUM-36 - Anomaly Deduplication (FR-14)

### Objective

Prevent repeated anomaly events while an abnormal
condition remains active.

### Deduplication strategy

The AnomalyDeduplicator maintains a set of currently
open anomalies.

Each anomaly is identified using:

    zoneId + anomalyType

Example:

    zone:3:PUMP_FAULT

The first anomaly is accepted.

Repeated anomalies with the same zone and type
are suppressed until recovery.

### Pump fault scenario

Input:

    100 consecutive PUMP_STATE = FAULT readings

Expected output:

    1 PUMP_FAULT anomaly

Recovery:

    PUMP_STATE = NORMAL

After recovery:

    A new PUMP_STATE = FAULT
    produces a new PUMP_FAULT anomaly.

### Probable leak scenario

When a leak is detected:

    LEAK_SUSPECTED is published.

If the leak persists:

    Repeated anomalies are suppressed.

If pressure or flow returns to normal:

    The open anomaly is cleared.

When the leak conditions are detected again:

    A new LEAK_SUSPECTED anomaly is allowed.

### Handling old measurements

The detection engine ignores pump-state readings
whose timestamps are older than or equal to the
latest accepted pump-state reading for that zone.

LeakRule also rejects older pressure and flow readings
when evaluating detection and recovery.

### Implementation

- AnomalyDeduplicator maintains the open anomaly keys.
- DetectionEngine applies deduplication after rule evaluation.
- Recovery clears the corresponding open anomaly key.

### Tests

Implemented tests include:

- AnomalyDeduplicatorTest
- DetectionEngineDeduplicationTest
- LeakRecoveryTest
- DeduplicationKafkaIntegrationTest

The Kafka integration scenario verifies:

1. Send 100 pump fault readings.
2. Verify that only one anomaly is published.
3. Send a NORMAL pump reading.
4. Send another FAULT reading.
5. Verify that a second anomaly is published.

Expected result:

    102 sensor readings
          |
          v
    2 PUMP_FAULT anomalies

This scenario has been successfully tested using
EmbeddedKafka.

## 5b. SCRUM-32 - Silent Sensor Detection (FR-10)

### Objective

Detect sensors that stopped sending readings.

### Detection rule

A sensor is considered silent when no reading has been received
from it for `SILENCE_THRESHOLD_SECONDS` seconds (default 10).
The service publishes one `SENSOR_SILENT` anomaly per silence period.
If the sensor sends a reading again, it is no longer silent and a new
silence can be detected later.

### Implementation

- `SilenceDetector` keeps the last reception time per sensor and checks
  every `SILENCE_CHECK_INTERVAL_MS` milliseconds (default 1000).
- It is called by `SensorReadingListener` for every reading, so the
  service uses a single Kafka listener.
- Time comes from an injected `Clock`, so tests use a simulated clock.
  The silence delay is counted from the reception time, not from the
  `measuredAt` field of the reading.
- The anomaly appears between T and T + 1 s after the last reading,
  within the required tolerance of T ± 2 s.
- `measuredAt` is the `measuredAt` of the last reading received from
  the sensor; `detectedAt` is the detection time.
- Anomalies are published directly with `AnomalyPublisher`.

### Tests

- SilenceDetectorTest: 5 tests with a simulated clock (no anomaly before
  the threshold, anomaly at the threshold, no duplicate, recovery,
  independent sensors).
- SilenceKafkaIntegrationTest: EmbeddedKafka test checking that the
  anomaly is published between T and T + 2 s.

### Limits

- Only sensors that sent at least one reading since the service started
  are monitored. A sensor that never sent anything is not detected.
- State is in memory and is lost on restart.
- Silent-sensor anomalies bypass `AnomalyDeduplicator` (keyed by zone and
  type), so several silent sensors in one zone each produce an anomaly.
- The Incident Service consumes `anomaly-detected`: a `SENSOR_SILENT`
  anomaly can create an incident. Stopping the simulator while the
  detection service is running therefore produces incidents.
- For `SENSOR_SILENT`, `measuredAt` is not the measurement that
  triggered the anomaly. It should be excluded from end-to-end latency
  calculations (NFR-02).

## 6. Kafka Integration

### Input topic

    sensor-readings

This topic contains sensor measurements generated
by the AquaOps simulation environment.

Supported reading types include:

- PRESSURE
- FLOW
- LEVEL
- PUMP_STATE

### Output topic

    anomaly-detected

The service publishes anomaly events to this topic.

Implemented anomaly types:

- PUMP_FAULT
- LEAK_SUSPECTED
- SENSOR_SILENT

The event contract also defines other anomaly types
that are not implemented by the current detection rules.

### Kafka configuration

Default connection:

    localhost:9092

Both the input and output topics can be configured
through environment variables.

## 7. Configuration

Configuration file:

    src/main/resources/application.yml

### Environment variables

| Variable | Default value |
|----------|---------------|
| DETECTION_PORT | 8083 |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 |
| DETECTION_SENSOR_TOPIC | sensor-readings |
| DETECTION_ANOMALY_TOPIC | anomaly-detected |
| DETECTION_CONSUMER_GROUP | detection-service |
| LEAK_MIN_PRESSURE | 2.0 |
| LEAK_MAX_FLOW | 40.0 |
| LEAK_DURATION_SECONDS | 10 |
| SILENCE_THRESHOLD_SECONDS | 10 |
| SILENCE_CHECK_INTERVAL_MS | 1000 |

Configuration values can be overridden without
modifying the Java source code.

## 8. Running the Service

### Prerequisites

- Java 21
- Apache Kafka
- Available Kafka input and output topics
- Git Bash or another compatible terminal

### Start Kafka

If the existing Docker container is stopped:

```bash
docker start aquaops-kafka
```

Check its status:

```bash
docker ps --filter name=aquaops-kafka
```

The default Kafka port is 9092.

### Start Detection Service

From the repository root:

```bash
cd services/detection-service
```

Start the application:

```bash
./mvnw spring-boot:run
```

By default, the application starts on port 8083.

### Health endpoint

When the service is running:

    http://localhost:8083/actuator/health

## 9. Testing and Validation

### Run all tests

From services/detection-service:

```bash
./mvnw clean test
```

### Latest validated result

Date: November 6, 2026

    Tests run: 45
    Failures: 0
    Errors: 0
    Skipped: 0

    BUILD SUCCESS

The message "Surefire is going to kill self fork JVM" may appear
after the tests when EmbeddedKafka takes a long time to stop. It
does not indicate a test failure.

### Validation coverage

The test suite includes:

- Pump fault rule tests
- Pump fault CSV scenarios
- Detection engine tests
- Pressure window tests
- Probable leak detection tests
- Timestamp freshness tests
- Zone independence tests
- Anomaly deduplication tests
- Recovery tests
- Out-of-order pump reading tests
- Silent sensor tests (simulated clock and tolerance)
- Kafka integration tests

### EmbeddedKafka

Kafka integration tests use a temporary embedded broker.

They validate the interaction between:

    Kafka Consumer
          |
          v
    DetectionEngine
          |
          v
    AnomalyPublisher
          |
          v
    Kafka Output Topic

The integration tests do not require the local
Docker Kafka container to be running.

### Code coverage

JaCoCo is configured in the Maven project.

After running the tests, the coverage report
is available at:

    target/site/jacoco/index.html

## 10. Integration with Other Services

The Detection Service receives sensor readings
through the sensor-readings Kafka topic.

It publishes anomaly-detected events for downstream
processing.

The Incident Service, developed by E4, is responsible
for processing anomalies according to its own
incident-management logic.

The Detection Service does not directly create
or persist incidents.

## 11. Known Limitations

### In-memory deduplication

The current anomaly state is stored in memory.

It is lost when the service restarts.

Persistent state management would be required
to preserve deduplication across restarts.

### Kafka delivery reliability

Anomaly publication uses an asynchronous Kafka producer.

The current detection flow does not provide
transactional guarantees between consuming sensor
readings and publishing anomaly events.

Additional error handling, retries and recovery
mechanisms are needed for stronger delivery reliability.

### Sensor grouping

Pump-state ordering and anomaly deduplication are
currently managed by zone and anomaly type.

If a zone contains multiple independent pumps,
the state model and keys may need to include
individual equipment identifiers.

Silent sensor detection is the exception: it keeps its own
state per sensor (see section 5b).

### Temporal assumptions

The pressure window is based on accepted sensor
timestamps.

The current implementation does not fully establish
continuous pressure monitoring when readings are missing
for long periods.

The pressure/flow timestamp proximity is fixed at
30 seconds.

These assumptions should be validated using the
sensor simulator and expected sampling frequency.

### Threshold validation

The default leak thresholds are testing values.

They require validation with AquaOps domain data
before operational deployment.

### Silent sensors

See the limits listed in section 5b: sensors that never sent a
reading are not monitored, and the state is lost on restart.

## 12. Development Summary

### Sprint 2

SCRUM-33 / FR-11:

- Implemented pump fault detection.
- Implemented Kafka consumer and producer.
- Added configurable Kafka properties.
- Added unit and integration tests.
- Added CSV-based test scenarios.

### Sprint 3

SCRUM-31 / FR-09:

- Implemented time-based low-pressure monitoring.
- Implemented probable leak detection.
- Added pressure/flow timestamp validation.
- Added unit and Kafka integration tests.

SCRUM-36 / FR-14:

- Implemented anomaly deduplication.
- Added pump and leak recovery handling.
- Added protection against old pump-state readings.
- Validated the 100-reading deduplication scenario
  with EmbeddedKafka.

SCRUM-32 / FR-10 (E1):

- Implemented the silent sensor scheduler with an injected Clock.
- Published SENSOR_SILENT anomalies through the existing publisher.
- Added simulated-clock tests and a Kafka tolerance test (T ± 2 s).

## 13. Project Ownership

Detection Service: E3

Silent sensor detection (SilenceDetector): E1

Main responsibilities:

- Maintain detection rules.
- Validate sensor readings and anomaly generation.
- Maintain Kafka integration with the detection pipeline.
- Implement and maintain automated tests.
- Coordinate anomaly event integration with E4.

The service is part of the AquaOps microservices
architecture and is intended to be integrated
through the team's GitHub Pull Request workflow.



## Image Docker

Construction (depuis la racine du dépôt) :

    docker build -f deploy/docker/spring-service.Dockerfile -t aquaops/detection-service:dev services/detection-service

Lancement (HEALTHCHECK_PORT = port du service ; KAFKA_BOOTSTRAP_SERVERS = adresse Kafka) :

    docker run -d -p 8083:8083 -e HEALTHCHECK_PORT=8083 \
      -e KAFKA_BOOTSTRAP_SERVERS=<adresse-kafka> aquaops/detection-service:dev

Santé : `curl http://localhost:8083/actuator/health`