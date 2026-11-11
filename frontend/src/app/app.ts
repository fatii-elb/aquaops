import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ForbiddenNotice } from './core/forbidden.interceptor';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    @if (notice.message(); as msg) {
      <div role="alert" style="background:#b91c1c;color:#fff;padding:.75rem 1rem;display:flex;justify-content:space-between;gap:1rem">
        <span>{{ msg }}</span>
        <button type="button" (click)="notice.clear()" style="color:#fff">Fermer</button>
      </div>
    }
    <router-outlet />
  `,
})
export class App {
  notice = inject(ForbiddenNotice);
}