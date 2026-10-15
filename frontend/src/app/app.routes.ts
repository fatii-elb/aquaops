import { Routes } from '@angular/router';
import { Login } from './features/login/login';
import { Dashboard } from './features/dashboard/dashboard';
import { Forbidden } from './features/forbidden/forbidden';
import { Administration } from './features/administration/administration';
import { authGuard, roleGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: 'administration', component: Administration, canActivate: [roleGuard('administrateur')] },
  { path: 'forbidden', component: Forbidden },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
