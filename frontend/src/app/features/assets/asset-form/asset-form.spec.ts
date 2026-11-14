import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AssetFormValue, AssetsSnapshot, CategoryKey, Asset } from '../assets.model';
import { AssetForm } from './asset-form';

const snapshot: AssetsSnapshot = {
  zones: [
    { id: 1, name: 'Zone 1', description: 'Première zone' },
    { id: 2, name: 'Zone 2', description: null },
  ],
  pipes: [
    { id: 1, name: 'Conduite Z1', zoneId: 1, lengthM: 500, diameterMm: 200 },
    { id: 2, name: 'Conduite Z2', zoneId: 2, lengthM: null, diameterMm: null },
  ],
  tanks: [],
  pumps: [],
  sensors: [],
};

function setup(category: CategoryKey, item: Asset | null = null, saving = false) {
  const fixture = TestBed.createComponent(AssetForm);
  fixture.componentRef.setInput('initialCategory', category);
  fixture.componentRef.setInput('item', item);
  fixture.componentRef.setInput('snapshot', snapshot);
  fixture.componentRef.setInput('saving', saving);
  const emitted: AssetFormValue[] = [];
  fixture.componentInstance.submitted.subscribe((v) => emitted.push(v));
  fixture.detectChanges();
  const el: HTMLElement = fixture.nativeElement;
  const field = <T extends HTMLElement>(name: string) => el.querySelector(`[formControlName="${name}"]`) as T;
  const type = (name: string, value: string) => {
    const input = field<HTMLInputElement>(name);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const choose = (name: string, index: number) => {
    const select = field<HTMLSelectElement>(name);
    select.selectedIndex = index;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const submit = () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };
  const options = (name: string) => Array.from(field<HTMLSelectElement>(name).options).map((o) => o.textContent?.trim());
  return { fixture, el, emitted, type, choose, submit, options, field };
}

describe('AssetForm', () => {
  it('refuse un nom vide et affiche le message', () => {
    const { el, emitted, submit } = setup('zones');
    submit();
    expect(emitted).toHaveLength(0);
    expect(el.querySelector('[data-testid="error-name"]')?.textContent).toContain('obligatoire');
  });

  it('émet une zone avec description nulle quand elle est vide, et nettoie les espaces du nom', () => {
    const { emitted, type, submit } = setup('zones');
    type('name', '  Zone 7  ');
    submit();
    expect(emitted).toEqual([{ category: 'zones', body: { name: 'Zone 7', description: null } }]);
  });

  it('exige une zone pour une conduite et refuse longueur négative et diamètre décimal', () => {
    const { el, emitted, type, choose, submit } = setup('pipes');
    type('name', 'C9');
    type('lengthM', '-4');
    type('diameterMm', '12.5');
    submit();
    expect(emitted).toHaveLength(0);
    expect(el.querySelector('[data-testid="error-zoneId"]')?.textContent).toContain('obligatoire');
    expect(el.textContent).toContain('strictement positive');
    expect(el.textContent).toContain('nombre entier');

    choose('zoneId', 1);
    type('lengthM', '120.5');
    type('diameterMm', '150');
    submit();
    expect(emitted).toEqual([{ category: 'pipes', body: { name: 'C9', zoneId: 1, lengthM: 120.5, diameterMm: 150 } }]);
  });

  it('accepte les champs facultatifs vides (envoyés à null)', () => {
    const { emitted, type, choose, submit } = setup('tanks');
    type('name', 'R1');
    choose('zoneId', 2);
    submit();
    expect(emitted).toEqual([{ category: 'tanks', body: { name: 'R1', zoneId: 2, capacityM3: null } }]);
  });

  it('un capteur ne propose que les actifs du type et de la zone choisis, et vide l’actif si la zone change', () => {
    const { options, choose, field } = setup('sensors');
    choose('zoneId', 1);
    choose('assetType', 1); // Conduite
    expect(options('assetId')).toEqual(['Choisir…', 'Conduite Z1']);
    choose('assetId', 1);
    expect((field<HTMLSelectElement>('assetId')).selectedIndex).toBe(1);
    choose('zoneId', 2);
    expect(options('assetId')).toEqual(['Choisir…', 'Conduite Z2']);
    expect((field<HTMLSelectElement>('assetId')).selectedIndex).toBe(0);
  });

  it('suggère le type d’actif selon le type de mesure, sans l’imposer', () => {
    const { choose, field } = setup('sensors');
    choose('type', 4); // PUMP_STATE
    expect(field<HTMLSelectElement>('assetType').selectedOptions[0].textContent).toContain('Pompe');
  });

  it('émet un capteur complet', () => {
    const { emitted, type, choose, submit } = setup('sensors');
    type('name', 'Pression Z1');
    choose('type', 1); // PRESSURE → conduite suggérée
    choose('zoneId', 1);
    choose('assetId', 1);
    submit();
    expect(emitted).toEqual([
      { category: 'sensors', body: { name: 'Pression Z1', type: 'PRESSURE', zoneId: 1, assetType: 'PIPE', assetId: 1 } },
    ]);
  });

  it('en modification : préremplit, fige la catégorie et renvoie tous les champs', () => {
    const { el, emitted, field, type, submit } = setup('zones', snapshot.zones[0]);
    expect(el.querySelector('[data-testid="field-category"]')).toBeNull();
    expect(field<HTMLInputElement>('name').value).toBe('Zone 1');
    expect(field<HTMLTextAreaElement>('description').value).toBe('Première zone');
    type('name', 'Zone 1 bis');
    submit();
    expect(emitted).toEqual([{ category: 'zones', body: { name: 'Zone 1 bis', description: 'Première zone' } }]);
  });

  it('en création, changer de type d’actif conserve le nom saisi', () => {
    const { fixture, el, field, type } = setup('zones');
    type('name', 'Mon actif');
    const select = el.querySelector('[data-testid="field-category"]') as HTMLSelectElement;
    select.value = 'pumps';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el.querySelector('[formControlName="description"]')).toBeNull();
    expect(field<HTMLInputElement>('name').value).toBe('Mon actif');
  });

  it('bloque l’envoi pendant l’enregistrement', () => {
    const { el, emitted, type, submit } = setup('zones', null, true);
    type('name', 'Z');
    expect((el.querySelector('[data-testid="form-submit"]') as HTMLButtonElement).disabled).toBe(true);
    submit();
    expect(emitted).toHaveLength(0);
  });

  it('affiche l’erreur renvoyée par le serveur sans vider le formulaire', () => {
    const fixture = TestBed.createComponent(AssetForm);
    fixture.componentRef.setInput('initialCategory', 'sensors');
    fixture.componentRef.setInput('snapshot', snapshot);
    fixture.componentRef.setInput('error', { kind: 'validation', status: 400, message: "La zone choisie n'existe pas ou plus." });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="form-error"]').textContent).toContain("La zone choisie n'existe pas");
  });
});
