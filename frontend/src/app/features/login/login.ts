import { Component, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  host: { class: 'block' },
})
export class Login implements OnDestroy {
  username = '';
  password = '';
  remember = true;

  showPassword = signal(false);
  now = signal(Login.formatNow());

  private timer = setInterval(() => this.now.set(Login.formatNow()), 30000);

  constructor(private router: Router) {}

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
    if (!this.username || !this.password) {
      return;
    }
    // TECH-6: pas de vraie authentification. Keycloak sera branché à l'étape SCRUM-45.
    this.router.navigate(['/dashboard']);
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }
}
