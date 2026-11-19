import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ThresholdValues } from '../thresholds.model';
import { ThresholdForm } from './threshold-form';

const initial: ThresholdValues = { leakMinPressure: 2, leakMaxFlow: 40, leakDurationSeconds: 10, silenceThresholdSeconds: 10, tankMinLevel: 2 };

function setup(inputs: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(ThresholdForm);
  fixture.componentRef.setInput('zoneName', 'Zone Nord');
  fixture.componentRef.setInput('initial', initial);
  for (const [k, v] of Object.entries(inputs)) {
    fixture.componentRef.setInput(k, v);
  }
  const emitted: ThresholdValues[] = [];
  fixture.componentInstance.submitted.subscribe((v) => emitted.push(v));
  fixture.detectChanges();
  const el: HTMLElement = fixture.nativeElement;
  const input = (key: string) => el.querySelector<HTMLInputElement>(`[data-field="${key}"]`)!;
  const type = (key: string, value: string) => {
    const i = input(key);
    i.value = value;
    i.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const submit = () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };
  return { fixture, el, emitted, input, type, submit };
}

describe('ThresholdForm', () => {
  it('préremplit les cinq seuils de la zone', () => {
    const { input } = setup();
    expect(input('leakMinPressure').value).toBe('2');
    expect(input('leakMaxFlow').value).toBe('40');
    expect(input('leakDurationSeconds').value).toBe('10');
    expect(input('silenceThresholdSeconds').value).toBe('10');
    expect(input('tankMinLevel').value).toBe('2');
  });

  it('envoie les valeurs modifiées', () => {
    const { emitted, type, submit } = setup();
    type('tankMinLevel', '3.5');
    type('leakMaxFlow', '55');
    submit();
    expect(emitted).toEqual([{ ...initial, tankMinLevel: 3.5, leakMaxFlow: 55 }]);
  });

  it('refuse les valeurs invalides avec un message par champ et n’envoie rien', () => {
    const { el, emitted, type, submit } = setup();
    type('leakMaxFlow', '0');
    type('leakDurationSeconds', '2.5');
    type('tankMinLevel', '-1');
    submit();
    expect(emitted).toHaveLength(0);
    expect(el.querySelector('[data-error="leakMaxFlow"]')!.textContent).toContain('strictement positif');
    expect(el.querySelector('[data-error="leakDurationSeconds"]')!.textContent).toContain('entier');
    expect(el.querySelector('[data-error="tankMinLevel"]')!.textContent).toContain('Minimum 0');
  });

  it('refuse un champ vidé', () => {
    const { el, emitted, type, submit } = setup();
    type('silenceThresholdSeconds', '');
    submit();
    expect(emitted).toHaveLength(0);
    expect(el.querySelector('[data-error="silenceThresholdSeconds"]')!.textContent).toContain('obligatoire');
  });

  it('bloque l’envoi pendant l’enregistrement (double soumission)', () => {
    const { el, emitted, submit } = setup({ saving: true });
    expect((el.querySelector('[data-testid="form-submit"]') as HTMLButtonElement).disabled).toBe(true);
    submit();
    expect(emitted).toHaveLength(0);
  });

  it('affiche les erreurs renvoyées par le serveur et le message général', () => {
    const { el } = setup({ serverErrors: { leakMaxFlow: 'Refusé par le serveur.' }, formError: 'Action refusée.' });
    expect(el.querySelector('[data-error="leakMaxFlow"]')!.textContent).toContain('Refusé par le serveur.');
    expect(el.querySelector('[data-testid="form-error"]')!.textContent).toContain('Action refusée.');
  });
});
