import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { AppConfig } from '../../core/app-config';
import { SensorRow, SensorType } from './sensor.model';

interface ApiSensor {
  id: number;
  name: string;
  type: SensorType;
  zoneId: number;
  assetType: 'PIPE' | 'TANK' | 'PUMP';
  assetId: number;
}

interface ApiZone {
  id: number;
  name: string;
}

interface ApiAsset {
  id: number;
  name: string;
}

interface ApiReading {
  value: number | null;
  unit: string | null;
  pumpState: string | null;
  measuredAt: string;
}

interface ApiPage<T> {
  content: T[];
}

interface AssetLists {
  pipes: ApiAsset[];
  tanks: ApiAsset[];
  pumps: ApiAsset[];
}

@Injectable({ providedIn: 'root' })
export class ApiSensorDataSource {
  private readonly http = inject(HttpClient);

  getSensors(config: AppConfig): Observable<SensorRow[]> {
    return forkJoin({
      sensors: this.http.get<ApiSensor[]>(`${config.assetsBaseUrl}/sensors`),
      zones: this.http.get<ApiZone[]>(`${config.assetsBaseUrl}/zones`),
      pipes: this.http.get<ApiAsset[]>(`${config.assetsBaseUrl}/pipes`),
      tanks: this.http.get<ApiAsset[]>(`${config.assetsBaseUrl}/tanks`),
      pumps: this.http.get<ApiAsset[]>(`${config.assetsBaseUrl}/pumps`),
    }).pipe(
      switchMap(({ sensors, zones, pipes, tanks, pumps }) => {
        if (sensors.length === 0) {
          return of([]);
        }

        const to = new Date();
        const from = new Date(to.getTime() - config.sensorLookbackSeconds * 1000);
        const assets: AssetLists = { pipes, tanks, pumps };
        return forkJoin(sensors.map((sensor) => this.latest(sensor, config, from, to))).pipe(
          map((readings) =>
            sensors
              .map((sensor, index) =>
                this.toRow(sensor, zones, assets, readings[index], config.sensorSilenceThresholdSeconds),
              )
              .sort((a, b) => a.id - b.id),
          ),
        );
      }),
    );
  }

  private latest(sensor: ApiSensor, config: AppConfig, from: Date, to: Date): Observable<ApiReading | null> {
    const params = new HttpParams()
      .set('sensor', sensor.id)
      .set('from', from.toISOString())
      .set('to', to.toISOString())
      .set('size', 1000);
    return this.http
      .get<ApiPage<ApiReading>>(`${config.telemetryBaseUrl}/readings`, { params })
      .pipe(map((page) => page.content.at(-1) ?? null));
  }

  private toRow(
    sensor: ApiSensor,
    zones: ApiZone[],
    assets: AssetLists,
    reading: ApiReading | null,
    silenceThresholdSeconds: number,
  ): SensorRow {
    const assetList = sensor.assetType === 'PIPE' ? assets.pipes : sensor.assetType === 'TANK' ? assets.tanks : assets.pumps;
    const asset = assetList.find((item) => item.id === sensor.assetId);
    const zone = zones.find((item) => item.id === sensor.zoneId);
    const ageSeconds = reading ? (Date.now() - new Date(reading.measuredAt).getTime()) / 1000 : Infinity;

    return {
      id: sensor.id,
      name: sensor.name,
      type: sensor.type,
      zoneId: sensor.zoneId,
      zoneName: zone?.name ?? `Zone ${sensor.zoneId}`,
      assetName: asset?.name ?? `${sensor.assetType} ${sensor.assetId}`,
      latestValue: reading?.value ?? null,
      unit: reading?.unit ?? null,
      pumpState: reading?.pumpState ?? null,
      measuredAt: reading?.measuredAt ?? null,
      state: ageSeconds <= silenceThresholdSeconds ? 'active' : 'silent',
    };
  }
}
