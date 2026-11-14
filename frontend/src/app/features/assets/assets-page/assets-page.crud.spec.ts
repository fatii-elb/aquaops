import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../../core/notification-stream';
import { FakeEventSource } from '../../incidents/test-helpers';
import { AssetsSnapshot } from '../assets.model';
import { AssetsPage } from './assets-page';

const DATA: AssetsSnapshot = {
  zones: [
    { id: 1, name: 'Zone 1', description: 'Zone de démonstration 1' },
    { id: 2, name: 'Zone 2', description: null },
    { id: 3, name: 'Zone vide', description: null },
  ],
  pipes: [
    { id: 1, name: 'Conduite Z1', zoneId: 1, lengthM: 500, diameterMm: 200 },
    { id: 2, name: 'Conduite Z2', zoneId: 2, lengthM: null, diameterMm: 150 },
  ],
  tanks: [{ id: 1, name: 'Réservoir Z1', zoneId: 1, capacityM3: 1000 }],
  pumps: [
    { id: 1, name: 'Pompe Z1', zoneId: 1 },
    { id: 2, name: 'Pompe Z2', zoneId: 2 },
  ],
  sensors: [
    { id: 1, name: 'Pression Z1', type: 'PRESSURE', zoneId: 1, assetType: 'PIPE', assetId: 1 },
    { id: 2, name: 'Débit Z1', type: 'FLOW', zoneId: 1, assetType: 'PIPE', assetId: 1 },
    { id: 3, name: 'Niveau Z1', type: 'LEVEL', zoneId: 1, assetType: 'TANK', assetId: 1 },
    { id: 4, name: 'État pompe Z1', type: 'PUMP_STATE', zoneId: 1, assetType: 'PUMP', assetId: 1 },
    { id: 5, name: 'Pression Z2', type: 'PRESSURE', zoneId: 2, assetType: 'PIPE', assetId: 2 },
  ],
};

const EMPTY: AssetsSnapshot = { zones: [], pipes: [], tanks: [], pumps: [], sensors: [] };
const KEYS = ['zones', 'pipes', 'tanks', 'pumps', 'sensors'] as const;

describe('AssetsPage : consultation et CRUD', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AssetsPage>;
  let el: HTMLElement;

  function setup(data: AssetsSnapshot = DATA, admin = true): void {
    TestBed.configureTestingModule({
      imports: [AssetsPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'u', hasAnyRole: () => admin, logout() {} } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => new FakeEventSource() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AssetsPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
    flush(data);
  }

  function flush(data: AssetsSnapshot): void {
    for (const key of KEYS) {
      http.expectOne(`/api/assets/${key}`).flush(data[key]);
    }
    fixture.detectChanges();
  }

  const q = <T extends Element = HTMLElement>(sel: string) => el.querySelector<T>(sel);
  const rows = () => el.querySelectorAll('tbody tr[data-row]');
  const click = (sel: string) => {
    q(sel)!.click();
    fixture.detectChanges();
  };
  const type = (sel: string, value: string) => {
    const input = q<HTMLInputElement>(sel)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const choose = (sel: string, index: number) => {
    const select = q<HTMLSelectElement>(sel)!;
    select.selectedIndex = index;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const submitForm = () => {
    q('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };
  const rowAction = (id: number, action: string) => click(`tr[data-row="${id}"] [data-action="${action}"]`);
  const kpi = (key: string) => q(`[data-testid="kpi-${key}"]`)?.textContent?.trim();

  beforeEach(() => TestBed.resetTestingModule());

  /* ---------------- consultation ---------------- */

  it('affiche les cinq catégories avec des colonnes propres à chacune', () => {
    setup();
    const expected: Array<[string, number, string]> = [
      ['zones', 3, 'Description'],
      ['pipes', 2, 'Longueur (m)'],
      ['tanks', 1, 'Capacité (m³)'],
      ['pumps', 2, 'Zone'],
      ['sensors', 5, 'Actif rattaché'],
    ];
    for (const [key, count, header] of expected) {
      click(`[data-tab="${key}"]`);
      expect(rows().length).toBe(count);
      expect(q('thead')!.textContent).toContain(header);
    }
  });

  it('affiche la répartition et les totaux calculés depuis les données', () => {
    setup();
    expect(q('[data-testid="distribution"]')).not.toBeNull();
    expect(q('[data-testid="total-length"]')!.textContent).toContain('500');
    expect(q('[data-testid="total-capacity"]')!.textContent).toContain('1');
  });

  it('un clic sur une carte KPI ouvre la catégorie correspondante', () => {
    setup();
    click('[data-kpi="sensors"]');
    expect(rows().length).toBe(5);
    expect(q('[data-tab="sensors"]')!.getAttribute('aria-selected')).toBe('true');
  });

  it('recherche rapide, absence de résultat, puis réinitialisation', () => {
    setup();
    click('[data-tab="sensors"]');
    type('[data-testid="search"]', 'debit');
    expect(rows().length).toBe(1);
    type('[data-testid="search"]', 'zzz');
    expect(q('[data-testid="no-results"]')).not.toBeNull();
    click('[data-testid="reset-filters"]');
    expect(rows().length).toBe(5);
  });

  it('combine le filtre de zone et le type de mesure', () => {
    setup();
    click('[data-tab="sensors"]');
    choose('[data-testid="filter-zone"]', 1); // Zone 1
    expect(rows().length).toBe(4);
    choose('[data-testid="filter-sensor-type"]', 1); // Pression
    expect(rows().length).toBe(1);
  });

  it('trie par colonne : croissant, puis décroissant', () => {
    setup();
    click('[data-tab="pipes"]');
    click('[data-sort="name"]');
    expect(rows()[0].getAttribute('data-row')).toBe('1');
    click('[data-sort="name"]');
    expect(rows()[0].getAttribute('data-row')).toBe('2');
  });

  it('pagine les listes longues', () => {
    const many = { ...EMPTY, zones: Array.from({ length: 12 }, (_, i) => ({ id: i + 1, name: `Z${i + 1}`, description: null })) };
    setup(many);
    expect(q('[data-testid="page-info"]')!.textContent).toContain('1–10 sur 12');
    expect(rows().length).toBe(10);
    click('[data-testid="page-next"]');
    expect(q('[data-testid="page-info"]')!.textContent).toContain('11–12 sur 12');
    expect(rows().length).toBe(2);
  });

  it('affiche un état vide explicite avec invitation à ajouter', () => {
    setup(EMPTY);
    expect(q('[data-testid="empty-state"]')).not.toBeNull();
    expect(q('[data-testid="empty-add"]')).not.toBeNull();
    expect(kpi('zones')).toBe('0');
  });

  /* ---------------- fiche et relations ---------------- */

  it('ouvre la fiche d’une zone, puis navigue vers un équipement et ses capteurs', () => {
    setup();
    click('tr[data-row="1"]');
    expect(q('[data-testid="asset-detail"]')).not.toBeNull();
    expect(q('[data-group="pipes"]')).not.toBeNull();
    click('[data-ref="pipes:1"]');
    expect(q('#detail-title')!.textContent).toContain('Conduite Z1');
    expect(q('[data-group="sensors"]')!.textContent).toContain('Pression Z1');
    click('[data-testid="close-detail"]');
    expect(q('[data-testid="asset-detail"]')).toBeNull();
  });

  /* ---------------- création ---------------- */

  it('crée une zone : validation, POST, notification, rafraîchissement des KPI', () => {
    setup();
    click('[data-testid="add-asset"]');
    submitForm();
    expect(q('[data-testid="error-name"]')!.textContent).toContain('obligatoire');
    http.expectNone('/api/assets/zones');

    type('[formControlName="name"]', 'Zone 4');
    submitForm();
    submitForm(); // double soumission : une seule requête
    const req = http.expectOne({ method: 'POST', url: '/api/assets/zones' });
    expect(req.request.body).toEqual({ name: 'Zone 4', description: null });
    req.flush({ id: 4, name: 'Zone 4', description: null });
    fixture.detectChanges();
    expect(q('[data-testid="toast"]')!.textContent).toContain('Zone « Zone 4 » créée');
    expect(q('[data-testid="asset-form"]')).toBeNull();

    flush({ ...DATA, zones: [...DATA.zones, { id: 4, name: 'Zone 4', description: null }] });
    expect(kpi('zones')).toBe('4');
    expect(rows().length).toBe(4);
  });

  it('refuse un capteur sans zone côté formulaire, sans appeler l’API', () => {
    setup();
    click('[data-tab="sensors"]');
    click('[data-testid="add-asset"]');
    type('[formControlName="name"]', 'Capteur orphelin');
    submitForm();
    expect(q('[data-testid="error-zoneId"]')!.textContent).toContain('obligatoire');
    http.expectNone('/api/assets/sensors');
  });

  it('affiche clairement le 400 d’un capteur dont la zone n’existe plus, sans fermer le formulaire', () => {
    setup();
    click('[data-tab="sensors"]');
    click('[data-testid="add-asset"]');
    type('[formControlName="name"]', 'Pression Z1 bis');
    choose('[formControlName="type"]', 1);
    choose('[formControlName="zoneId"]', 1);
    choose('[formControlName="assetId"]', 1);
    submitForm();
    const req = http.expectOne({ method: 'POST', url: '/api/assets/sensors' });
    expect(req.request.body).toEqual({ name: 'Pression Z1 bis', type: 'PRESSURE', zoneId: 1, assetType: 'PIPE', assetId: 1 });
    req.flush({ status: 400, error: 'Bad Request' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(q('[data-testid="form-error"]')!.textContent).toContain('doivent exister');
    expect(q('[data-testid="asset-form"]')).not.toBeNull();
    flush(DATA); // rechargement après un refus de validation
  });

  /* ---------------- modification ---------------- */

  it('modifie une zone en conservant les champs non modifiés', () => {
    setup();
    rowAction(1, 'edit');
    expect(q<HTMLInputElement>('[formControlName="name"]')!.value).toBe('Zone 1');
    type('[formControlName="name"]', 'Zone 1 bis');
    submitForm();
    const req = http.expectOne({ method: 'PUT', url: '/api/assets/zones/1' });
    expect(req.request.body).toEqual({ name: 'Zone 1 bis', description: 'Zone de démonstration 1' });
    req.flush({ id: 1, name: 'Zone 1 bis', description: 'Zone de démonstration 1' });
    fixture.detectChanges();
    expect(q('[data-testid="toast"]')!.textContent).toContain('modifiée');
    flush({ ...DATA, zones: [{ ...DATA.zones[0], name: 'Zone 1 bis' }, DATA.zones[1], DATA.zones[2]] });
    expect(q('tr[data-row="1"]')!.textContent).toContain('Zone 1 bis');
  });

  /* ---------------- suppression ---------------- */

  it('bloque la suppression d’une zone qui contient encore des équipements', () => {
    setup();
    rowAction(1, 'delete');
    expect(q('[data-testid="delete-blocked"]')).not.toBeNull();
    expect(q('[data-testid="confirm-delete"]')).toBeNull();
    http.expectNone({ method: 'DELETE', url: '/api/assets/zones/1' });
    click('[data-testid="cancel-delete"]');
    expect(q('[data-testid="delete-dialog"]')).toBeNull();
  });

  it('bloque la suppression d’une conduite équipée de capteurs', () => {
    setup();
    click('[data-tab="pipes"]');
    rowAction(1, 'delete');
    expect(q('[data-testid="delete-blocked"]')!.textContent).toContain('2');
  });

  it('supprime une zone vide après confirmation, puis met à jour les KPI', () => {
    setup();
    rowAction(3, 'delete');
    expect(q('[data-testid="delete-dialog"]')!.textContent).toContain('Zone vide');
    click('[data-testid="confirm-delete"]');
    http.expectOne({ method: 'DELETE', url: '/api/assets/zones/3' }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(q('[data-testid="toast"]')!.textContent).toContain('supprimée');
    flush({ ...DATA, zones: DATA.zones.slice(0, 2) });
    expect(kpi('zones')).toBe('2');
  });

  it('affiche le refus 403 lors d’une suppression', () => {
    setup();
    rowAction(3, 'delete');
    click('[data-testid="confirm-delete"]');
    http.expectOne({ method: 'DELETE', url: '/api/assets/zones/3' }).flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(q('[data-testid="delete-error"]')!.textContent).toContain('administrateur');
    expect(q('[data-testid="delete-dialog"]')).not.toBeNull();
  });

  /* ---------------- permissions ---------------- */

  it('sans le rôle administrateur : consultation seulement, aucune action d’écriture', () => {
    setup(DATA, false);
    expect(q('[data-testid="add-asset"]')).toBeNull();
    expect(q('[data-action="edit"]')).toBeNull();
    expect(q('[data-action="delete"]')).toBeNull();
    expect(q('[data-action="view"]')).not.toBeNull();
    click('tr[data-row="1"]');
    expect(q('[data-testid="detail-edit"]')).toBeNull();
  });
});
