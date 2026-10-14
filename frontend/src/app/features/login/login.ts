import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <main class="box">
      <h1>AquaOps</h1>
      <p>Supervision du réseau d'eau</p>
      <form (ngSubmit)="submit()">
        <label>Utilisateur
          <input name="username" [(ngModel)]="username" required />
        </label>
        <label>Mot de passe
          <input name="password" type="password" [(ngModel)]="password" required />
        </label>
        <button type="submit" [disabled]="!username || !password">Se connecter</button>
      </form>
      <small>Connexion Keycloak prévue à l'étape SCRUM-45.</small>
    </main>
  `,
  styles: [`
    .box { max-width: 340px; margin: 10vh auto; display: grid; gap: 12px; font-family: sans-serif; }
    label { display: grid; gap: 4px; }
    input, button { padding: 8px; }
  `],
})
export class Login {
  username = '';
  password = '';

  constructor(private router: Router) {}

  submit() {
    this.router.navigate(['/dashboard']);
  }
}
