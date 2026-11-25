import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../core/notification-stream';
import { InterventionApi } from '../interventions/intervention-api';
import { Intervention } from '../interventions/intervention.model';
import { Administration } from './administration';
import { keycloakConsoleUrl, summarizeTeams } from './administration-rules';

const item = (id: number, team: string | null, status: Intervention['status'], assignedTo: string | null = null): Intervention => ({
  id,
  incidentId: id,
  team,
  assignedTo,
  status,
  createdAt: '2026-12-08T10:00:00Z',
});

describe('Administration : règles', () => {
  it('regroupe les interventions par équipe, compte ouvertes et terminées, ignore les sans-équipe', () => {
    const teams = summarizeTeams([
      item(1, 'Équipe B', 'PLANNED', 'tech-1'),
      item(2, 'Équipe A', 'DONE', 'tech-2'),
      item(3, 'Équipe A', 'IN_PROGRESS', 'tech-2'),
      item(4, null, 'PLANNED'),
      item(5, '  ', 'PLANNED'),
    ]);
    expect(teams.map((t) => t.name)).toEqual(['Équipe A', 'Équipe B']);
    expect(teams[0]).toMatchObject({ total: 2, open: 1, done: 1, members: ['tech-2'] });
    expect(teams[1]).toMatchObject({ total: 1, open: 1, done: 0, members: ['tech-1'] });
  });

  it("déduit l'adresse de la console Keycloak de l'issuer", () => {
    expect(keycloakConsoleUrl('http://localhost:8180/realms/aquaops')).toBe('http://localhost:8180/admin/master/console/#/aquaops');
    expect(keycloakConsoleUrl('pas une adresse')).toBeNull();
  });
});

async function render(list: () => Observable<Intervention[]>) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: InterventionApi, useValue: { list } },
      { provide: AuthService, useValue: { username: 'admin', hasAnyRole: () => true, logout: () => undefined } },
      { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
    ],
  });
  const fixture = TestBed.createComponent(Administration);
  fixture.detectChanges();
  await fixture.whenStable();
  // La page lit config.json de façon asynchrone (adresse de la console Keycloak) : on laisse ce temps au test.
  await new Promise((resolve) => setTimeout(resolve, 100));
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Administration : page', () => {
  it('affiche les équipes déduites des interventions', async () => {
    const root = await render(() => of([item(1, 'Équipe A', 'PLANNED'), item(2, 'Équipe B', 'DONE')]));
    expect(root.querySelectorAll('[data-testid="team-row"]')).toHaveLength(2);
  });

  it("affiche l'état vide quand aucune équipe n'existe", async () => {
    const root = await render(() => of([]));
    expect(root.querySelectorAll('[data-testid="team-row"]')).toHaveLength(0);
    expect(root.textContent).toContain('Aucune équipe');
  });

  it("affiche un message d'accès refusé en cas de 403", async () => {
    const root = await render(() => throwError(() => new HttpErrorResponse({ status: 403 })));
    expect(root.textContent).toContain('Accès refusé');
  });

  it('affiche un message réseau quand le service est injoignable', async () => {
    const root = await render(() => throwError(() => new HttpErrorResponse({ status: 0 })));
    expect(root.textContent).toContain('injoignable');
  });

  it('ouvre Keycloak et les seuils, et marque les autres outils « Bientôt disponible »', async () => {
    const root = await render(() => of([]));
    const links = root.querySelectorAll('a[data-testid="tool-link"]');
    expect(links).toHaveLength(2);
    expect(links[0].getAttribute('href')).toContain('/admin/master/console/#/aquaops');
    expect(links[1].getAttribute('href')).toBe('/thresholds');
    expect(links[1].getAttribute('target')).toBeNull();
    expect(root.querySelectorAll('[data-testid="tool-soon"]')).toHaveLength(2);
  });

  it('précise que la création d\'équipes est indisponible', async () => {
    const root = await render(() => of([]));
    expect(root.querySelector('[data-testid="teams-note"]')?.textContent).toContain('création d\'équipes n\'est pas disponible');
  });
});
