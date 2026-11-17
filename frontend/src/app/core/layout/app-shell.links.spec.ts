import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../auth.service';
import { EVENT_SOURCE_FACTORY } from '../notification-stream';
import { AppShell } from './app-shell';

describe('AppShell : liens des pages d\'attente (FE-6)', () => {
  it('Télémétrie, Rapports, Tableau de bord et Incidents sont des liens pour tous les rôles', () => {
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
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    // /observability n'est plus dans cette liste : FE-11 la réserve à superviseur et administrateur.
    for (const href of ['/telemetry', '/reports', '/dashboard', '/incidents']) {
      expect(el.querySelector(`nav a[href="${href}"]`), href).not.toBeNull();
    }
  });

  it('Observabilité est masquée pour un rôle sans droit (FE-11)', () => {
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
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('nav a[href="/observability"]')).toBeNull();
  });

  it('Observabilité est visible pour un superviseur (FE-11)', () => {
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'u', hasAnyRole: (roles: string[]) => roles.includes('superviseur'), logout() {} } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
      ],
    });
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('nav a[href="/observability"]')).not.toBeNull();
  });
});