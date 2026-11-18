import { Observable } from 'rxjs';
import { SensorReading, SensorRow } from './sensor.model';

export abstract class SensorDataSource {
  abstract getSensors(): Observable<SensorRow[]>;
  abstract getHistory(sensorId: number, from: Date, to: Date): Observable<SensorReading[]>;
}
