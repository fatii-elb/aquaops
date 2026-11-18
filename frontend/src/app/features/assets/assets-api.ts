import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import { Asset, AssetFormValue, AssetInput, AssetMap, AssetsSnapshot, CategoryKey, categoryMeta } from './assets.model';

/**
 * Accès à l'API des actifs (assets-service, via la passerelle).
 * Seuls les endpoints réellement exposés par le backend sont utilisés :
 * GET (liste et détail), POST, PUT et DELETE sur /api/assets/{zones|pipes|tanks|pumps|sensors}.
 * Le jeton est ajouté par authInterceptor ; les droits restent contrôlés par le serveur.
 */
@Injectable({ providedIn: 'root' })
export class AssetsApi {
  private http = inject(HttpClient);
  private readonly base = '/api/assets';

  private url(category: CategoryKey, id?: number): string {
    const path = `${this.base}/${categoryMeta(category).path}`;
    return id === undefined ? path : `${path}/${id}`;
  }

  list<K extends CategoryKey>(category: K): Observable<AssetMap[K][]> {
    return this.http.get<AssetMap[K][]>(this.url(category));
  }

  get<K extends CategoryKey>(category: K, id: number): Observable<AssetMap[K]> {
    return this.http.get<AssetMap[K]>(this.url(category, id));
  }

  create<K extends CategoryKey>(category: K, body: AssetInput<K>): Observable<AssetMap[K]> {
    return this.http.post<AssetMap[K]>(this.url(category), body);
  }

  /** Le PUT du backend remplace l'élément entier : il faut renvoyer tous les champs. */
  update<K extends CategoryKey>(category: K, id: number, body: AssetInput<K>): Observable<AssetMap[K]> {
    return this.http.put<AssetMap[K]>(this.url(category, id), body);
  }

  /** Création (id nul : POST) ou modification (PUT) à partir de la valeur du formulaire. */
  save(value: AssetFormValue, id: number | null): Observable<Asset> {
    const url = this.url(value.category, id ?? undefined);
    return id === null ? this.http.post<Asset>(url, value.body) : this.http.put<Asset>(url, value.body);
  }

  remove(category: CategoryKey, id: number): Observable<void> {
    return this.http.delete<void>(this.url(category, id));
  }

  /** Charge les cinq listes en parallèle. Échoue si l'une d'elles échoue. */
  loadAll(): Observable<AssetsSnapshot> {
    return forkJoin({
      zones: this.list('zones'),
      pipes: this.list('pipes'),
      tanks: this.list('tanks'),
      pumps: this.list('pumps'),
      sensors: this.list('sensors'),
    });
  }
}
