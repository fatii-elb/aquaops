import { Observable } from 'rxjs';
import { SensorRow } from './sensor.model';

export abstract class SensorDataSource {
  abstract getSensors(): Observable<SensorRow[]>;
}
