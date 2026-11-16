import { Routes } from '@angular/router';
import { Login } from './features/login/login';
import { Dashboard } from './features/dashboard/dashboard';
import { Forbidden } from './features/forbidden/forbidden';
import { Administration } from './features/administration/administration';
import { Audit } from './features/audit/audit';
import { authGuard, roleGuard } from './core/auth.guard';
import { IncidentCenter } from './features/incidents/incident-center/incident-center';
import { AssetsPage } from './features/assets/assets-page/assets-page';
import { ComingSoon } from './features/placeholder/coming-soon';
import { AnomaliesPage } from './features/anomalies/anomalies-page';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: 'incidents', component: IncidentCenter, canActivate: [authGuard] },
  { path: 'assets', component: AssetsPage, canActivate: [roleGuard('administrateur')] },
  { path: 'alerts', component: AnomaliesPage, canActivate: [authGuard] },
  { path: 'incidents/:id', component: IncidentCenter, canActivate: [authGuard] },
  {
    path: 'telemetry',
    component: ComingSoon,
    canActivate: [authGuard],
    data: { title: 'Télémétrie & Capteurs', active: 'telemetry', icon: 'sensors', description: 'Les courbes et les dernières valeurs des capteurs arrivent bientôt.' },
  },
  {
    path: 'reports',
    component: ComingSoon,
    canActivate: [authGuard],
    data: { title: 'Rapports & Analyses', active: 'reports', icon: 'analytics', description: 'Les rapports et les analyses du réseau arrivent bientôt.' },
  },
  {
    path: 'observability',
    component: ComingSoon,
    canActivate: [authGuard],
    data: { title: 'Observabilité', active: 'observability', icon: 'monitor_heart', description: "Les tableaux de bord d'observabilité arrivent bientôt." },
  },
  { path: 'administration', component: Administration, canActivate: [roleGuard('administrateur')] },
  { path: 'audit', component: Audit, canActivate: [roleGuard('superviseur', 'administrateur')] },
  { path: 'forbidden', component: Forbidden },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
