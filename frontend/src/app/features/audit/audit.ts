import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppShell } from '../../core/layout/app-shell';
import { AuditDataSource } from './audit-data-source';
import { AUDIT_DATA_SOURCE_CONFIG } from './audit-data-source.config';
import { actionLabel, entityLabel, errorMessage, periodToIso } from './audit-format';
import { AuditEntry, AuditQuery } from './audit.model';

/**
 * Journal d'audit (FR-24, carte FE-5) : qui a fait quoi et quand.
 * Réservé aux rôles superviseur et administrateur (voir app.routes.ts) ; l'API refuse aussi les autres (403).
 */
@Component({
  selector: 'app-audit',
  imports: [FormsModule, AppShell],
  templateUrl: './audit.html',
  host: { class: 'block' },
})
export class Audit implements OnInit {
  private source = inject(AuditDataSource);

  readonly pageSize = AUDIT_DATA_SOURCE_CONFIG.pageSize;

  // Champs du formulaire de filtres.
  actor = '';
  action = '';
  from = '';
  to = '';

  readonly entries = signal<AuditEntry[]>([]);
  readonly actions = signal<string[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  /** Seule la réponse à la dernière requête compte (un clic rapide ne doit pas être écrasé par une vieille réponse). */
  private requestId = 0;

  ngOnInit(): void {
    this.source.actions().subscribe((actions) => this.actions.set(actions));
    this.load(0);
  }

  /** Bouton « Filtrer » : retour à la première page avec les filtres saisis. */
  apply(): void {
    if (this.from && this.to && this.from > this.to) {
      this.error.set('La date de début doit précéder la date de fin.');
      return;
    }
    this.load(0);
  }

  reset(): void {
    this.actor = '';
    this.action = '';
    this.from = '';
    this.to = '';
    this.load(0);
  }

  previous(): void {
    if (this.page() > 0) this.load(this.page() - 1);
  }

  next(): void {
    if (this.page() + 1 < this.totalPages()) this.load(this.page() + 1);
  }

  private query(page: number): AuditQuery {
    const query: AuditQuery = { page, size: this.pageSize, ...periodToIso(this.from, this.to) };
    if (this.actor.trim()) query.actor = this.actor.trim();
    if (this.action) query.action = this.action;
    return query;
  }

  private load(page: number): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    this.source.search(this.query(page)).subscribe({
      next: (result) => {
        if (id !== this.requestId) return;
        this.entries.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
        this.loaded.set(true);
      },
      error: (error: unknown) => {
        if (id !== this.requestId) return;
        this.entries.set([]);
        this.totalPages.set(0);
        this.totalElements.set(0);
        this.error.set(errorMessage(error));
        this.loading.set(false);
        this.loaded.set(true);
      },
    });
  }

  // ----- Affichage -----
  actionLabel = actionLabel;
  entityLabel = entityLabel;

  when(iso: string): string {
    const d = new Date(iso);
    return `${d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' })} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}`;
  }
}
