import { Component, OnInit, input, output, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { FieldErrors, validateField } from '../thresholds.rules';
import { THRESHOLD_FIELDS, ThresholdField, ThresholdKey, ThresholdValues } from '../thresholds.model';

function fieldValidator(field: ThresholdField) {
  return (control: AbstractControl): ValidationErrors | null => {
    const message = validateField(field, control.value);
    return message ? { threshold: message } : null;
  };
}

/** Formulaire des seuils d'une zone, dans un panneau latéral. */
@Component({
  selector: 'app-threshold-form',
  imports: [ReactiveFormsModule],
  templateUrl: './threshold-form.html',
  host: { '(document:keydown.escape)': 'cancelled.emit()' },
})
export class ThresholdForm implements OnInit {
  readonly zoneName = input.required<string>();
  readonly initial = input.required<ThresholdValues>();
  readonly saving = input(false);
  /** Erreurs de champ renvoyées par le serveur. */
  readonly serverErrors = input<FieldErrors>({});
  /** Erreur générale (droits, serveur injoignable…). */
  readonly formError = input<string | null>(null);

  readonly submitted = output<ThresholdValues>();
  readonly cancelled = output<void>();

  protected readonly fields = THRESHOLD_FIELDS;
  protected form = new FormGroup<Record<string, FormControl<number | null>>>({});
  protected readonly attempted = signal(false);

  ngOnInit(): void {
    const controls: Record<string, FormControl<number | null>> = {};
    for (const field of THRESHOLD_FIELDS) {
      controls[field.key] = new FormControl<number | null>(this.initial()[field.key], fieldValidator(field));
    }
    this.form = new FormGroup(controls);
  }

  protected submit(): void {
    this.attempted.set(true);
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitted.emit(this.form.getRawValue() as unknown as ThresholdValues);
  }

  /** Erreur affichée sous un champ : celle du formulaire d'abord, sinon celle du serveur. */
  protected errorOf(key: ThresholdKey): string | null {
    const control = this.form.controls[key];
    if (control && control.invalid && (control.touched || this.attempted())) {
      return (control.errors?.['threshold'] as string | undefined) ?? 'Valeur invalide.';
    }
    return this.serverErrors()[key] ?? null;
  }
}
