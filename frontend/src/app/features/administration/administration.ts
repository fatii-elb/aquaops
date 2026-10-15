import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-administration',
  imports: [RouterLink],
  template: `
    <main style="font-family: sans-serif; margin: 2rem">
      <h1>Administration</h1>
      <p>Page réservée au rôle <strong>administrateur</strong>. Connecté : {{ auth.username }}</p>
      <a routerLink="/dashboard">Retour au tableau de bord</a>
    </main>
  `,
})
export class Administration {
  auth = inject(AuthService);
}
