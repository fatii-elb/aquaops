import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ZoneInfo } from './incident.model';

/** Noms des zones (service des actifs). Si le service est injoignable, on affiche « Zone N ». */
@Injectable({ providedIn: 'root' })
export class ZoneNames {
  private http = inject(HttpClient);
  private requested = false;

  readonly zones = signal<ZoneInfo[]>([]);

  /** Charge la liste une seule fois. */
  load(): void {
    if (this.requested) {
      return;
    }
    this.requested = true;
    this.http
      .get<ZoneInfo[] | { content?: ZoneInfo[] }>('/api/assets/zones')
      .pipe(catchError(() => of([] as ZoneInfo[])))
      .subscribe((response) => this.zones.set(Array.isArray(response) ? response : (response.content ?? [])));
  }

  name(id: number): string {
    return this.zones().find((zone) => zone.id === id)?.name ?? `Zone ${id}`;
  }
}
