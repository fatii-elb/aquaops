# Page Rapports & Analyses (FE-10 et FE-13)

Route `/reports`, réservée aux rôles `superviseur` et `administrateur` (garde de rôle, comme le journal d'audit ; l'API refuse aussi les autres en 403).

## Ce que fait la page

- **Période** : 7, 30 ou 90 derniers jours, tout, ou dates personnalisées (jours entiers, en UTC). Elle s'applique aux indicateurs et au rapport.
- **Indicateurs (FE-10)** : incidents de la période (dont encore actifs), résolus ou clos, temps moyen de résolution, interventions terminées ; barres par priorité, par type et par zone ; courbe des incidents par jour.
- **Rapport (FE-13)** : tableau d'incidents filtrable par priorité, état et zone (en plus de la période), paginé par 20.
- **Export CSV** : exactement les lignes filtrées, toutes pages confondues (séparateur « ; », UTF-8 avec BOM pour Excel, formules neutralisées).
- **Version imprimable** : bouton « Imprimer » (ou Ctrl+P) : le menu disparaît, toutes les lignes s'affichent. Choisir « Enregistrer au format PDF » dans la fenêtre d'impression.

## Sources de données

`reports-data-source.ts` : `mode: 'api'` (par défaut) ou `'mock'`.

| Donnée | API |
|---|---|
| Incidents | `GET /api/incidents` |
| Interventions | `GET /api/assets/interventions` (si elle échoue : l'indicateur affiche « — ») |
| Noms des zones | `GET /api/assets/zones` (si elle échoue : « Zone N ») |

Mode `'mock'` : jeu de données de `reports-sample-data.ts` (12 incidents, 6 interventions, 3 zones), daté par rapport à aujourd'hui. Les mêmes données servent aux tests, avec des résultats comptés à la main.

## Limites à confirmer avec E4 et le PO

1. **Temps de résolution** : l'API des incidents ne donne pas de date de résolution. Le calcul prend la date de dernière mise à jour (`updatedAt`) des incidents `RESOLVED` ou `CLOSED`. C'est une approximation : un incident clos longtemps après sa résolution, ou clos directement depuis « Ouvert », fausse la moyenne. Demander un champ `resolvedAt` à E4.
2. **Capteurs silencieux** (indicateur cité dans FE-10) : non inclus, il dépend de l'API des mesures et de la page FE-7 (E1).
3. **Filtrage côté Angular** : l'API des incidents ne filtre que par statut et zone, sans pagination ni période : tout est chargé puis filtré dans le navigateur. Acceptable pour la démonstration ; à revoir si le volume grossit.
4. **Socle commun FE-6** (E3) : pas encore fusionné, donc la page a ses propres tableau, filtres et graphique. À remplacer par ceux de FE-6 une fois disponibles.

## Fichiers

- Nouveaux : `frontend/src/app/features/reports/` (page, logique, sources de données, jeu de données, tests).
- Modifiés (quelques lignes) : `app.routes.ts` (route), `core/layout/app-shell.ts` (lien du menu), `features/dashboard/dashboard.html` et `dashboard.ts` (lien visible pour superviseur et administrateur).

## Tests

```bash
cd frontend
npm ci
npx ng test --no-watch
```
