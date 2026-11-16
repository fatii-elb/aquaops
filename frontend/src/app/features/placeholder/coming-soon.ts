import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AppShell } from '../../core/layout/app-shell';

/**
 * Page d'attente d'une fonctionnalité pas encore développée. Les informations viennent de la route :
 * data: { title, active, icon, description }. Le responsable de la fonctionnalité remplace la route par sa page.
 */
@Component({
  selector: 'app-coming-soon',
  imports: [AppShell],
  templateUrl: './coming-soon.html',
})
export class ComingSoon {
  private data = inject(ActivatedRoute).snapshot.data;

  protected readonly title: string = this.data['title'] ?? 'Bientôt disponible';
  protected readonly active: string = this.data['active'] ?? 'dashboard';
  protected readonly icon: string = this.data['icon'] ?? 'construction';
  protected readonly description: string =
    this.data['description'] ?? "Cette fonctionnalité est en cours de développement.";
}
