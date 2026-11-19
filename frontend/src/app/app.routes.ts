import { Routes } from '@angular/router';
import { Login } from './features/login/login';
import { Dashboard } from './features/dashboard/dashboard';
import { Forbidden } from './features/forbidden/forbidden';
import { Administration } from './features/administration/administration';
import { Audit } from './features/audit/audit';
import { authGuard, roleGuard } from './core/auth.guard';
import { IncidentCenter } from './features/incidents/incident-center/incident-center';
import { SensorList } from './features/telemetry/sensor-list';
import { SensorHistory } from './features/telemetry/sensor-history';
import { AnomalyDataSource } from './features/anomalies/anomaly-data-source';
import { ANOMALY_DATA_SOURCE_CONFIG } from './features/anomalies/anomaly-data-source.config';
import { ApiAnomalyDataSource } from './features/anomalies/api-anomaly-data-source';
import { MockAnomalyDataSource } from './features/anomalies/mock-anomaly-data-source';
import { AssetsPage } from './features/assets/assets-page/assets-page';
import { ComingSoon } from './features/placeholder/coming-soon';
import { AnomaliesPage } from './features/anomalies/anomalies-page';
import { ThresholdsPage } from './features/thresholds/thresholds-page';
import { ApiReportsDataSource } from './features/reports/api-reports-data-source';
import { MockReportsDataSource } from './features/reports/mock-reports-data-source';
import { REPORTS_DATA_SOURCE_CONFIG, ReportsDataSource } from './features/reports/reports-data-source';
import { Reports } from './features/reports/reports';
import { Observability } from './features/observability/observability';
import { MockServiceStatusDataSource } from './features/observability/mock-service-status-data-source';
import { ServiceStatusDataSource } from './features/observability/service-status-data-source';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: 'incidents', component: IncidentCenter, canActivate: [authGuard] },
  { path: 'assets', component: AssetsPage, canActivate: [roleGuard('administrateur')] },
  { path: 'alerts', component: AnomaliesPage, canActivate: [authGuard] },
  { path: 'incidents/:id', component: IncidentCenter, canActivate: [authGuard] },
  {
    path: 'telemetry',
    component: SensorList,
    canActivate: [roleGuard('operateur', 'superviseur', 'administrateur')],
  },
  {
    path: 'telemetry/:sensorId',
    component: SensorHistory,
    canActivate: [roleGuard('operateur', 'superviseur', 'administrateur')],
    providers: [
      {
        provide: AnomalyDataSource,
        useClass: ANOMALY_DATA_SOURCE_CONFIG.mode === 'mock' ? MockAnomalyDataSource : ApiAnomalyDataSource,
      },
    ],
  },
  {
    // Observabilité (FE-11) : superviseur et administrateur. Source 'mock' pour l'instant, 'api' plus tard.
    path: 'observability',
    component: Observability,
    canActivate: [roleGuard('superviseur', 'administrateur')],
    providers: [{ provide: ServiceStatusDataSource, useClass: MockServiceStatusDataSource }],
  },
  { path: 'thresholds', component: ThresholdsPage, canActivate: [roleGuard('administrateur')] },
  { path: 'administration', component: Administration, canActivate: [roleGuard('administrateur')] },
  { path: 'audit', component: Audit, canActivate: [roleGuard('superviseur', 'administrateur')] },
  {
    // Rapports & Analyses (FE-10, FE-13) : superviseur et administrateur. Source 'api' ou 'mock' : voir reports-data-source.ts.
    path: 'reports',
    component: Reports,
    canActivate: [roleGuard('superviseur', 'administrateur')],
    providers: [{ provide: ReportsDataSource, useClass: REPORTS_DATA_SOURCE_CONFIG.mode === 'api' ? ApiReportsDataSource : MockReportsDataSource }],
  },
  { path: 'forbidden', component: Forbidden },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
