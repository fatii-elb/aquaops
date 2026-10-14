import { Routes } from '@angular/router';
import { Login } from './features/login/login';
import { Dashboard } from './features/dashboard/dashboard';
import { Forbidden } from './features/forbidden/forbidden';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'dashboard', component: Dashboard },
  { path: 'forbidden', component: Forbidden },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
