import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ForbiddenNotice, forbiddenInterceptor } from './forbidden.interceptor';

describe('forbiddenInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let notice: ForbiddenNotice;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([forbiddenInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    notice = TestBed.inject(ForbiddenNotice);
  });

  afterEach(() => {
    httpMock.verify();
  });

  /** Envoie un GET, répond avec le statut voulu, renvoie l'erreur reçue par l'appelant. */
  function appelEnErreur(url: string, status: number): { error?: HttpErrorResponse } {
    const resultat: { error?: HttpErrorResponse } = {};
    http.get(url).subscribe({ error: (e: HttpErrorResponse) => (resultat.error = e) });
    httpMock.expectOne(url).flush('refus', { status, statusText: 'Erreur' });
    return resultat;
  }

  it('affiche le message sur un 403 de /api/ et relance l’erreur', () => {
    const resultat = appelEnErreur('/api/assets', 403);

    expect(notice.message()).toContain('Action refusée');
    expect(resultat.error?.status).toBe(403);
  });

  it('n’affiche rien sur un 403 hors /api/', () => {
    const resultat = appelEnErreur('/autre/chemin', 403);

    expect(notice.message()).toBeNull();
    expect(resultat.error?.status).toBe(403);
  });

  it('n’affiche rien sur une erreur 500 de /api/ et relance l’erreur', () => {
    const resultat = appelEnErreur('/api/assets', 500);

    expect(notice.message()).toBeNull();
    expect(resultat.error?.status).toBe(500);
  });

  it('clear() efface le message', () => {
    appelEnErreur('/api/assets', 403);
    expect(notice.message()).not.toBeNull();

    notice.clear();

    expect(notice.message()).toBeNull();
  });
});