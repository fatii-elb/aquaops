import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OAuthService } from 'angular-oauth2-oidc';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  function setup(token: string) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: OAuthService, useValue: { getAccessToken: () => token } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  }

  afterEach(() => controller.verify());

  it('ajoute le jeton aux appels /api/', () => {
    setup('abc');
    http.get('/api/assets/zones').subscribe();
    const req = controller.expectOne('/api/assets/zones');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc');
    req.flush([]);
  });

  it("n'ajoute rien aux autres adresses", () => {
    setup('abc');
    http.get('https://exemple.org/data').subscribe();
    const req = controller.expectOne('https://exemple.org/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it("n'ajoute rien quand l'utilisateur n'est pas connecté (pas de jeton)", () => {
    setup('');
    http.get('/api/assets/zones').subscribe();
    const req = controller.expectOne('/api/assets/zones');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });
});
