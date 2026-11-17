import { Injectable, inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { loadAppConfig } from '../../core/app-config';
import { ApiSensorDataSource } from './api-sensor-data-source';
import { MockSensorDataSource } from './mock-sensor-data-source';
import { SensorDataSource } from './sensor-data-source';

@Injectable()
export class ConfiguredSensorDataSource extends SensorDataSource {
  private readonly api = inject(ApiSensorDataSource);
  private readonly mock = inject(MockSensorDataSource);
  private readonly config = loadAppConfig();

  getSensors() {
    return from(this.config).pipe(
      switchMap((config) =>
        config.sensorDataMode === 'api'
          ? this.api.getSensors(config)
          : this.mock.getSensors(config.sensorSilenceThresholdSeconds),
      ),
    );
  }

  getHistory(sensorId: number, fromDate: Date, toDate: Date) {
    return from(this.config).pipe(
      switchMap((config) =>
        config.sensorDataMode === 'api'
          ? this.api.getHistory(config, sensorId, fromDate, toDate)
          : this.mock.getHistory(sensorId, fromDate, toDate),
      ),
    );
  }
}
