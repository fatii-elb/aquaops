# Frontend (Angular) — owner: E4, support: E5

FE-7 provides the sensor list and latest readings at `/telemetry`. FE-8 provides
the selected sensor's reading history at `/telemetry/:sensorId`, with a numeric
trend chart or a pump-state timeline, 1-hour, 24-hour, 7-day and custom ranges,
indicative thresholds from the dashboard defaults, paginated readings and CSV
export. Anomaly markers are shown when the detection API is available. The page
uses the configured telemetry data source.
