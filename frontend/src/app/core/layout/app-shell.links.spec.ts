import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../auth.service';
import { EVENT_SOURCE_FACTORY } from '../notification-stream';
import { AppShell } from './app-shell';

describe('AppShell : liens des pages d\'attente (FE-6)', () => {
  it('Tableau de bord, Télémétrie, Incidents et Observabilité sont des liens pour tous les rôles', () => {
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
    for (const href of ['/telemetry', '/observability', '/dashboard', '/incidents']) {
      expect(el.querySelector(`nav a[href="${href}"]`), href).not.toBeNull();
    }
  });

  function render(hasAnyRole: () => boolean): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'u', hasAnyRole, logout() {} } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
      ],
    });
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('Rapports, Actifs, Administration et Journal d\'audit sont réservés aux rôles autorisés', () => {
    const restricted = ['/reports', '/assets', '/administration', '/audit'];
    const sansRole = render(() => false);
    for (const href of restricted) expect(sansRole.querySelector(`nav a[href="${href}"]`), `sans rôle ${href}`).toBeNull();
    const avecRole = render(() => true);
    for (const href of restricted) expect(avecRole.querySelector(`nav a[href="${href}"]`), `avec rôle ${href}`).not.toBeNull();
  });
});
