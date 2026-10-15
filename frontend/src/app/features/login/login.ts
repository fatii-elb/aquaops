import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  host: { class: 'block' },
})
export class Login implements OnDestroy {
  private auth = inject(AuthService);
  private router = inject(Router);

  // Champs du formulaire : la saisie réelle se fait sur la page de Keycloak.
  username = '';
  password = '';
  remember = true;

  showPassword = signal(false);
  now = signal(Login.formatNow());

  private timer = setInterval(() => this.now.set(Login.formatNow()), 30000);

  constructor() {
    // Retour de Keycloak : si le jeton est valide, on va directement au tableau de bord.
    if (this.auth.isLoggedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  private static formatNow(): string {
    const d = new Date();
    const date = d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
    const time = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return `${date} • ${time}`;
  }

  togglePassword() {
    this.showPassword.update((v) => !v);
  }

  submit() {
    this.auth.login();
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }
}
