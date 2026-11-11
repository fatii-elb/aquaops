import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';
import { HasRoleDirective } from './has-role.directive';

@Component({
  selector: 'app-hote-test',
  imports: [HasRoleDirective],
  template: `<button *appHasRole="roles">Supprimer</button>`,
})
class HoteTestComponent {
  roles: string[] = ['administrateur'];
}

function creer(rolesUtilisateur: string[], rolesDemandes: string[]): ComponentFixture<HoteTestComponent> {
  TestBed.configureTestingModule({
    imports: [HoteTestComponent],
    providers: [
      {
        provide: AuthService,
        useValue: {
          hasAnyRole: (wanted: string[]) => wanted.some((role) => rolesUtilisateur.includes(role)),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(HoteTestComponent);
  fixture.componentInstance.roles = rolesDemandes;
  fixture.detectChanges();
  return fixture;
}

describe('HasRoleDirective', () => {
  it('affiche l’élément si l’utilisateur a le bon rôle', () => {
    const fixture = creer(['administrateur'], ['administrateur']);

    expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
  });

  it('masque l’élément si l’utilisateur n’a pas le rôle', () => {
    const fixture = creer(['operateur'], ['administrateur']);

    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('affiche l’élément si l’utilisateur a un des rôles demandés', () => {
    const fixture = creer(['superviseur'], ['administrateur', 'superviseur']);

    expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
  });
});