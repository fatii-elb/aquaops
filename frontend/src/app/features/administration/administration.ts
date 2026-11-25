import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { loadAppConfig } from '../../core/app-config';
import { AppShell } from '../../core/layout/app-shell';
import { describeHttpError } from '../../shared/http-errors';
import { StateBlock } from '../../shared/state-block/state-block';
import { InterventionApi } from '../interventions/intervention-api';
import { ADMIN_TOOLS, TeamSummary, keycloakConsoleUrl, summarizeTeams } from './administration-rules';

/**
 * Page Administration (FE-12). Réservée à l'administrateur (voir app.routes.ts).
 * Ne recrée ni la gestion des utilisateurs (console Keycloak), ni les actifs, ni le journal d'audit.
 * Les équipes sont en lecture seule : aucune API d'équipes n'existe, l'équipe est saisie à l'affectation.
 */
@Component({
  selector: 'app-administration',
  imports: [AppShell, StateBlock, RouterLink],
  templateUrl: './administration.html',
})
export class Administration implements OnInit {
  private api = inject(InterventionApi);

  readonly state = signal<'loading' | 'ready' | 'empty' | 'error'>('loading');
  readonly teams = signal<TeamSummary[]>([]);
  readonly error = signal('');
  readonly consoleUrl = signal<string | null>(null);

  readonly tools = computed(() =>
    ADMIN_TOOLS.map((tool) => ({ ...tool, href: tool.key === 'keycloak' ? this.consoleUrl() : tool.key === 'thresholds' ? '/thresholds' : null })),
  );

  ngOnInit(): void {
    this.load();
    void loadAppConfig().then((config) => this.consoleUrl.set(keycloakConsoleUrl(config.issuer)));
  }

  load(): void {
    this.state.set('loading');
    this.api.list().subscribe({
      next: (list) => {
        const teams = summarizeTeams(list);
        this.teams.set(teams);
        this.state.set(teams.length === 0 ? 'empty' : 'ready');
      },
      error: (err: unknown) => {
        this.error.set(describeHttpError(err, 'les équipes').message);
        this.state.set('error');
      },
    });
  }
}
