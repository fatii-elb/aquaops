import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, throwError } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ForbiddenNotice {
  readonly message = signal<string | null>(null);

  show(): void {
    this.message.set("Action refusée : votre rôle ne permet pas cette opération.");
  }

  clear(): void {
    this.message.set(null);
  }
}

/** Affiche un message clair quand notre API répond 403, puis relance l'erreur. */
export const forbiddenInterceptor: HttpInterceptorFn = (req, next) => {
  const notice = inject(ForbiddenNotice);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 403 && req.url.startsWith('/api/')) {
        notice.show();
      }
      return throwError(() => error);
    }),
  );
};