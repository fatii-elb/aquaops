import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink],
  template: `<main style="font-family:sans-serif;margin:2rem"><h1>Accès refusé</h1><p>Votre rôle ne permet pas d'ouvrir cette page.</p><a routerLink="/login">Retour</a></main>`,
})
export class Forbidden {}
