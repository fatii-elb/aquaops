import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../auth.service';
import { EVENT_SOURCE_FACTORY } from '../notification-stream';
import { AppShell } from './app-shell';

function render(count: number | null): HTMLElement {
  TestBed.configureTestingModule({
    imports: [AppShell],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { username: 'u', hasAnyRole: () => false, logout() {} } },
      { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
    ],
  });
  const fixture = TestBed.createComponent(AppShell);
  fixture.componentRef.setInput('alertsCount', count);
  fixture.detectChanges();
  return fixture.nativeElement;
}

describe('AppShell : Alertes & Anomalies (FE-9)', () => {
  it("l'entrée du menu est un lien vers /alerts", () => {
    expect(render(null).querySelector('nav a[href="/alerts"]')).not.toBeNull();
  });

  it('affiche le nombre d’anomalies ouvertes', () => {
    const badge = render(4).querySelector('[data-testid="alerts-count"]');
    expect(badge?.textContent?.trim()).toBe('4');
  });

  it('n’affiche aucune pastille quand le compteur est nul ou absent', () => {
    expect(render(0).querySelector('[data-testid="alerts-count"]')).toBeNull();
  });

  it('n’affiche aucune pastille sans compteur (pages qui ne le fournissent pas)', () => {
    expect(render(null).querySelector('[data-testid="alerts-count"]')).toBeNull();
  });
});
