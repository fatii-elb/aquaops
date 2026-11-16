import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../core/notification-stream';
import { FakeEventSource } from '../incidents/test-helpers';
import { Incident, ZoneInfo } from '../incidents/incident.model';
import { Intervention } from '../interventions/intervention.model';
import { CSV_DOWNLOAD } from './csv-download';
import { ReportsDataSource } from './reports-data-source';
import { SAMPLE_ZONES, sampleIncidents, sampleInterventions } from './reports-sample-data';
import { Reports } from './reports';

class StubSource extends ReportsDataSource {
  constructor(
    private data: Incident[],
    private done: Intervention[] | null,
    private fail = false,
  ) {
    super();
  }
  incidents(): Observable<Incident[]> {
    return this.fail ? throwError(() => ({ error: { message: 'Service indisponible' } })) : of(this.data);
  }
  interventions(): Observable<Intervention[] | null> {
    return of(this.done);
  }
  zones(): Observable<ZoneInfo[]> {
    return of(SAMPLE_ZONES);
  }
}

// Les incidents sont datés par rapport à « maintenant » : la période « 30 derniers jours » donne 9 incidents.
const NOW = new Date();

async function render(source: ReportsDataSource) {
  const downloads: { content: string; name: string }[] = [];
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ReportsDataSource, useValue: source },
      { provide: CSV_DOWNLOAD, useValue: (content: string, name: string) => downloads.push({ content, name }) },
      { provide: AuthService, useValue: { username: 'sup', hasAnyRole: () => true, logout: () => undefined } },
      { provide: EVENT_SOURCE_FACTORY, useValue: () => new FakeEventSource() },
    ],
  });
  const fixture = TestBed.createComponent(Reports);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const q = (testId: string) => root.querySelector(`[data-testid="${testId}"]`) as HTMLElement;
  const text = (testId: string) => q(testId)?.textContent?.replace(/\s+/g, ' ').trim();
  const choose = async (testId: string, value: string) => {
    const select = q(testId) as HTMLSelectElement | HTMLInputElement;
    select.value = value;
    select.dispatchEvent(new Event(select.tagName === 'INPUT' ? 'input' : 'change'));
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const click = async (testId: string) => {
    q(testId).click();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const rowCount = () => root.querySelectorAll('[data-testid="row"]').length;
  return { fixture, root, q, text, choose, click, rowCount, downloads };
}

describe('Reports', () => {
  beforeEach(() => TestBed.resetTestingModule());
  afterEach(() => vi.restoreAllMocks());

  const source = () => new StubSource(sampleIncidents(NOW), sampleInterventions(NOW));

  it('affiche les indicateurs des 30 derniers jours', async () => {
    const { text } = await render(source());
    expect(text('kpi-total')).toBe('9');
    expect(text('kpi-finished')).toBe('6');
    expect(text('kpi-resolution')).toBe('3 h 30 min');
    expect(text('kpi-interventions')).toBe('3');
  });

  it('changer de période met à jour tous les indicateurs et le rapport', async () => {
    const { text, choose, rowCount } = await render(source());
    await choose('filter-period', '7d');
    expect(text('kpi-total')).toBe('5');
    expect(text('kpi-finished')).toBe('3');
    expect(text('kpi-resolution')).toBe('4 h');
    expect(text('kpi-interventions')).toBe('2');
    expect(rowCount()).toBe(5);
    await choose('filter-period', 'all');
    expect(text('kpi-total')).toBe('12');
  });

  it('période sans incident : message dédié', async () => {
    const { q, choose } = await render(new StubSource([], []));
    expect(q('empty-period').textContent).toContain('Aucun incident sur la période.');
    expect(q('empty')).toBeTruthy();
    await choose('filter-period', '7d');
    expect(q('empty-period')).toBeTruthy();
  });

  it('dates personnalisées incohérentes : message et aucun résultat', async () => {
    const { q, choose, rowCount } = await render(source());
    await choose('filter-period', 'custom');
    await choose('filter-from', '2026-12-08');
    await choose('filter-to', '2026-12-01');
    expect(q('range-error').textContent).toContain('La date de début doit précéder la date de fin.');
    expect(rowCount()).toBe(0);
  });

  it('les filtres du rapport se combinent ; les indicateurs restent ceux de la période', async () => {
    const { text, choose, rowCount } = await render(source());
    await choose('filter-priority', 'CRITICAL');
    expect(rowCount()).toBe(2); // n° 1 et 6 dans les 30 jours
    await choose('filter-zone', '1');
    expect(rowCount()).toBe(2);
    await choose('filter-status', 'OPEN');
    expect(rowCount()).toBe(0);
    expect(text('kpi-total')).toBe('9');
  });

  it('pagination : 20 lignes par page, retour à la page 1 quand un filtre change', async () => {
    const many = Array.from({ length: 45 }, (_, i) => ({ ...sampleIncidents(NOW)[0], id: i + 1 }));
    const { rowCount, click, text, choose } = await render(new StubSource(many, []));
    expect(rowCount()).toBe(20);
    expect(text('summary')).toBe('Page 1 sur 3 · 45 incidents');
    await click('next');
    await click('next');
    expect(rowCount()).toBe(5);
    await choose('filter-priority', 'CRITICAL');
    expect(text('summary')).toBe('Page 1 sur 3 · 45 incidents');
  });

  it('exporte exactement les lignes filtrées, toutes pages confondues', async () => {
    const many = Array.from({ length: 45 }, (_, i) => ({ ...sampleIncidents(NOW)[0], id: i + 1 }));
    const { click, downloads } = await render(new StubSource(many, []));
    await click('export');
    expect(downloads).toHaveLength(1);
    expect(downloads[0].name).toMatch(/^rapport-incidents-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(downloads[0].content.trimEnd().split('\r\n')).toHaveLength(1 + 45);
  });

  it('exporte le même nombre de lignes que le tableau filtré', async () => {
    const { click, choose, rowCount, downloads } = await render(source());
    await choose('filter-priority', 'HIGH');
    const shown = rowCount();
    await click('export');
    expect(downloads[0].content.trimEnd().split('\r\n')).toHaveLength(1 + shown);
    expect(downloads[0].content).toContain('Centre-ville'); // nom de zone, pas son numéro
  });

  it('bouton d\'export désactivé quand le rapport est vide', async () => {
    const { q } = await render(new StubSource([], []));
    expect((q('export') as HTMLButtonElement).disabled).toBe(true);
  });

  it('interventions indisponibles : tiret et le reste s\'affiche', async () => {
    const { text } = await render(new StubSource(sampleIncidents(NOW), null));
    expect(text('kpi-interventions')).toBe('—');
    expect(text('kpi-total')).toBe('9');
  });

  it('API des incidents en erreur : message clair, pas d\'indicateurs', async () => {
    const { q } = await render(new StubSource([], [], true));
    expect(q('error').textContent).toContain('Service indisponible');
    expect(q('kpi-total')).toBeNull();
  });

  it('impression : toutes les lignes sont affichées, puis retour à la pagination', async () => {
    const many = Array.from({ length: 45 }, (_, i) => ({ ...sampleIncidents(NOW)[0], id: i + 1 }));
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    const { fixture, rowCount, click } = await render(new StubSource(many, []));
    expect(rowCount()).toBe(20);
    window.dispatchEvent(new Event('beforeprint'));
    fixture.detectChanges();
    expect(rowCount()).toBe(45);
    window.dispatchEvent(new Event('afterprint'));
    fixture.detectChanges();
    expect(rowCount()).toBe(20);
    await click('print');
    await new Promise((resolve) => setTimeout(resolve, 80));
    expect(print).toHaveBeenCalledTimes(1);
  });
});
