import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { AuthService } from './auth.service';

/** Affiche l'élément seulement si l'utilisateur a l'un des rôles donnés. */
@Directive({ selector: '[appHasRole]' })
export class HasRoleDirective {
  private auth = inject(AuthService);
  private template = inject(TemplateRef<unknown>);
  private container = inject(ViewContainerRef);

  readonly appHasRole = input.required<string[]>();

  constructor() {
    effect(() => {
      this.container.clear();
      if (this.auth.hasAnyRole(this.appHasRole())) {
        this.container.createEmbeddedView(this.template);
      }
    });
  }
}