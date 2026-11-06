package ma.aquaops.simulator;

import ma.aquaops.simulator.scenario.ReadingType;

public record Sensor(int sensorId, int zoneId, ReadingType type) {}