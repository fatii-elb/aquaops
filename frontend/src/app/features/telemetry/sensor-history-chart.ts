import { SensorReading } from './sensor.model';

export const SENSOR_HISTORY_CHART_WIDTH = 600;
export const SENSOR_HISTORY_CHART_HEIGHT = 180;
const MAX_CHART_DOTS = 600;

export interface SensorHistoryChart {
  points: string;
  dots: { x: number; y: number; measuredAt: string; value: number }[];
  min: number | null;
  max: number | null;
  scaleMin: number | null;
  scaleMax: number | null;
  timeStart: number | null;
  timeEnd: number | null;
}

export function sensorHistoryChart(
  readings: SensorReading[],
  scaleValues: number[] = [],
): SensorHistoryChart {
  const valid = readings
    .filter(
      (reading): reading is SensorReading & { value: number } =>
        reading.value !== null &&
        Number.isFinite(reading.value) &&
        !Number.isNaN(Date.parse(reading.measuredAt)),
    )
    .sort((a, b) => Date.parse(a.measuredAt) - Date.parse(b.measuredAt));
  if (valid.length === 0) {
    return {
      points: '',
      dots: [],
      min: null,
      max: null,
      scaleMin: null,
      scaleMax: null,
      timeStart: null,
      timeEnd: null,
    };
  }

  const { min, max } = valid.reduce(
    (range, reading) => ({
      min: Math.min(range.min, reading.value),
      max: Math.max(range.max, reading.value),
    }),
    { min: Number.POSITIVE_INFINITY, max: Number.NEGATIVE_INFINITY },
  );
  const scale = scaleValues.reduce(
    (range, value) => ({ min: Math.min(range.min, value), max: Math.max(range.max, value) }),
    { min, max },
  );
  const visible = downsample(valid);
  const left = 32;
  const right = SENSOR_HISTORY_CHART_WIDTH - 16;
  const top = 12;
  const bottom = SENSOR_HISTORY_CHART_HEIGHT - 20;
  const firstTime = Date.parse(valid[0].measuredAt);
  const lastTime = Date.parse(valid[valid.length - 1].measuredAt);
  const dots = visible.map((reading) => {
    const time = Date.parse(reading.measuredAt);
    const x =
      firstTime === lastTime
        ? (left + right) / 2
        : Math.round(left + ((time - firstTime) / (lastTime - firstTime)) * (right - left));
    const y =
      scale.min === scale.max
        ? (top + bottom) / 2
        : Math.round(
            bottom - ((reading.value - scale.min) / (scale.max - scale.min)) * (bottom - top),
          );
    return { x, y, measuredAt: reading.measuredAt, value: reading.value };
  });

  return {
    points: dots.map(({ x, y }) => `${x},${y}`).join(' '),
    dots,
    min,
    max,
    scaleMin: scale.min,
    scaleMax: scale.max,
    timeStart: firstTime,
    timeEnd: lastTime,
  };
}

export function sensorChartY(value: number, min: number | null, max: number | null): number | null {
  if (min === null || max === null) return null;
  const top = 12;
  const bottom = SENSOR_HISTORY_CHART_HEIGHT - 20;
  return min === max
    ? (top + bottom) / 2
    : Math.round(bottom - ((value - min) / (max - min)) * (bottom - top));
}

export function sensorChartX(
  value: number,
  start: number | null,
  end: number | null,
): number | null {
  if (start === null || end === null) return null;
  const left = 32;
  const right = SENSOR_HISTORY_CHART_WIDTH - 16;
  return start === end
    ? (left + right) / 2
    : Math.round(left + ((value - start) / (end - start)) * (right - left));
}

function downsample(
  readings: (SensorReading & { value: number })[],
): (SensorReading & { value: number })[] {
  if (readings.length <= MAX_CHART_DOTS) {
    return readings;
  }

  const bucketCount = Math.floor((MAX_CHART_DOTS - 2) / 2);
  const interiorLength = readings.length - 2;
  const indexes = new Set([0, readings.length - 1]);
  for (let bucket = 0; bucket < bucketCount; bucket++) {
    const start = 1 + Math.floor((bucket * interiorLength) / bucketCount);
    const end = 1 + Math.floor(((bucket + 1) * interiorLength) / bucketCount);
    let minIndex = start;
    let maxIndex = start;
    for (let index = start + 1; index < end; index++) {
      if (readings[index].value < readings[minIndex].value) minIndex = index;
      if (readings[index].value > readings[maxIndex].value) maxIndex = index;
    }
    indexes.add(minIndex);
    indexes.add(maxIndex);
  }
  return [...indexes].sort((a, b) => a - b).map((index) => readings[index]);
}
