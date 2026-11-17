export type SensorType = 'PRESSURE' | 'FLOW' | 'LEVEL' | 'PUMP_STATE';
export type SensorState = 'active' | 'silent';

export interface SensorRow {
  id: number;
  name: string;
  type: SensorType;
  zoneId: number;
  zoneName: string;
  assetName: string;
  latestValue: number | null;
  unit: string | null;
  pumpState: string | null;
  measuredAt: string | null;
  state: SensorState;
}

export interface SensorReading {
  value: number | null;
  unit: string | null;
  pumpState: string | null;
  measuredAt: string;
}
