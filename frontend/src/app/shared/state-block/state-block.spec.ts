import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { StateBlock, StateKind } from './state-block';

function render(kind: StateKind, inputs: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(StateBlock);
  fixture.componentRef.setInput('kind', kind);
  for (const [k, v] of Object.entries(inputs)) {
    fixture.componentRef.setInput(k, v);
  }
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

describe('StateBlock', () => {
  it('chargement : squelette avec le nombre de lignes demandé', () => {
    const { el } = render('loading', { rows: 3 });
    expect(el.querySelector('[data-testid="state-loading"]')).not.toBeNull();
    expect(el.querySelectorAll('.animate-pulse').length).toBe(3);
  });

  it('vide : titre et message', () => {
    const { el } = render('empty', { title: 'Aucune anomalie', message: 'Tout va bien.' });
    expect(el.querySelector('[data-testid="state-empty"]')!.textContent).toContain('Aucune anomalie');
    expect(el.textContent).toContain('Tout va bien.');
  });

  it('erreur : message et bouton Réessayer qui émet', () => {
    const { fixture, el } = render('error', { message: 'Service injoignable.' });
    let retries = 0;
    fixture.componentInstance.retry.subscribe(() => retries++);
    expect(el.querySelector('[data-testid="state-error"]')!.textContent).toContain('Service injoignable.');
    (el.querySelector('[data-testid="state-retry"]') as HTMLButtonElement).click();
    expect(retries).toBe(1);
  });

  it('erreur non réessayable : pas de bouton', () => {
    const { el } = render('error', { message: 'Accès refusé.', retryable: false });
    expect(el.querySelector('[data-testid="state-retry"]')).toBeNull();
  });
});
