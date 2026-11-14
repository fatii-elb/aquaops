import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { AssetsError } from '../assets-rules';
import {
  ASSET_KIND_LABELS,
  ASSET_KINDS,
  Asset,
  AssetFormValue,
  AssetKind,
  AssetsSnapshot,
  CATEGORIES,
  CategoryKey,
  Pipe,
  Pump,
  SENSOR_TYPE_LABELS,
  SENSOR_TYPES,
  Sensor,
  SensorType,
  Tank,
  categoryMeta,
} from '../assets.model';

type FieldValue = string | number | null;
/** Les champs dépendent de la catégorie : le groupe est volontairement non typé champ par champ. */
type AssetFormGroup = FormGroup;

/** Valeur absente (champ facultatif vide) ou nombre strictement positif. */
function positive(control: AbstractControl): ValidationErrors | null {
  const v = control.value;
  return v === null || v === '' || Number(v) > 0 ? null : { positive: true };
}

function integer(control: AbstractControl): ValidationErrors | null {
  const v = control.value;
  return v === null || v === '' || Number.isInteger(Number(v)) ? null : { integer: true };
}

/** Type d'actif le plus courant pour chaque type de capteur (suggestion, jamais imposée). */
const SUGGESTED_KIND: Record<SensorType, AssetKind> = {
  PRESSURE: 'PIPE',
  FLOW: 'PIPE',
  LEVEL: 'TANK',
  PUMP_STATE: 'PUMP',
};

/** Formulaire de création ou de modification d'un actif, affiché dans un panneau latéral. */
@Component({
  selector: 'app-asset-form',
  imports: [ReactiveFormsModule],
  templateUrl: './asset-form.html',
  host: { '(document:keydown.escape)': 'cancelled.emit()' },
})
export class AssetForm implements OnInit {
  private destroyRef = inject(DestroyRef);

  /** Catégorie proposée à l'ouverture ; modifiable en création, figée en modification. */
  readonly initialCategory = input.required<CategoryKey>();
  /** Élément à modifier ; nul en création. */
  readonly item = input<Asset | null>(null);
  readonly snapshot = input.required<AssetsSnapshot>();
  readonly saving = input(false);
  readonly error = input<AssetsError | null>(null);

  readonly submitted = output<AssetFormValue>();
  readonly cancelled = output<void>();

  protected readonly categories = CATEGORIES;
  protected readonly sensorTypes = SENSOR_TYPES;
  protected readonly sensorTypeLabels = SENSOR_TYPE_LABELS;
  protected readonly assetKinds = ASSET_KINDS;
  protected readonly assetKindLabels = ASSET_KIND_LABELS;

  protected readonly category = signal<CategoryKey>('zones');
  protected readonly attempted = signal(false);
  protected form: AssetFormGroup = new FormGroup({});

  private readonly zoneSel = signal<number | null>(null);
  private readonly kindSel = signal<AssetKind | null>(null);

  protected readonly meta = computed(() => categoryMeta(this.category()));
  protected readonly editing = computed(() => this.item() !== null);

  /** Actifs proposés pour un capteur : ceux du type choisi, dans la zone choisie. */
  protected readonly assetOptions = computed(() => {
    const kind = this.kindSel();
    const zone = this.zoneSel();
    if (!kind) {
      return [];
    }
    const s = this.snapshot();
    const list: Array<Pipe | Tank | Pump> = kind === 'PIPE' ? s.pipes : kind === 'TANK' ? s.tanks : s.pumps;
    const options = list.filter((a) => zone === null || a.zoneId === zone).map((a) => ({ id: a.id, label: a.name }));
    // En modification, l'actif actuel reste proposé même s'il se trouve dans une autre zone.
    const current = this.item() as Sensor | null;
    if (current && current.assetType === kind && !options.some((o) => o.id === current.assetId)) {
      const original = list.find((a) => a.id === current.assetId);
      if (original) {
        options.push({ id: original.id, label: `${original.name} (autre zone)` });
      }
    }
    return options;
  });

  ngOnInit(): void {
    this.category.set(this.initialCategory());
    this.form = this.buildForm(this.category(), this.item());
    this.watch();
  }

  protected changeCategory(value: string): void {
    const next = value as CategoryKey;
    const previous = this.form.getRawValue();
    this.category.set(next);
    this.attempted.set(false);
    this.form = this.buildForm(next, null);
    // Le nom et la zone déjà saisis sont conservés quand on change de type d'actif.
    this.form.controls['name']?.setValue(previous['name'] ?? '');
    this.form.controls['zoneId']?.setValue((previous['zoneId'] as number | null | undefined) ?? null);
    this.watch();
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
    this.submitted.emit(this.toValue());
  }

  /** Message d'erreur d'un champ, une fois touché ou après une tentative d'envoi. */
  protected errorOf(name: string): string | null {
    const control = this.form.controls[name];
    if (!control || control.valid || !(control.touched || this.attempted())) {
      return null;
    }
    if (control.hasError('required')) {
      return name === 'name' ? 'Le nom est obligatoire.' : 'Ce champ est obligatoire.';
    }
    if (control.hasError('maxlength')) {
      return 'Maximum 255 caractères.';
    }
    if (control.hasError('positive')) {
      return 'La valeur doit être strictement positive.';
    }
    if (control.hasError('integer')) {
      return 'Saisissez un nombre entier.';
    }
    return 'Valeur invalide.';
  }

  private buildForm(category: CategoryKey, item: Asset | null): AssetFormGroup {
    const text = (key: string, validators: Array<(c: AbstractControl) => ValidationErrors | null>, fallback: FieldValue = '') =>
      new FormControl<FieldValue>(((item as unknown as Record<string, FieldValue> | null)?.[key] ?? fallback), validators);
    const name = text('name', [Validators.required, Validators.maxLength(255)]);
    const zone = text('zoneId', [Validators.required], null);
    switch (category) {
      case 'zones':
        return new FormGroup({ name, description: text('description', [Validators.maxLength(255)]) });
      case 'pipes':
        return new FormGroup({
          name,
          zoneId: zone,
          lengthM: text('lengthM', [positive], null),
          diameterMm: text('diameterMm', [positive, integer], null),
        });
      case 'tanks':
        return new FormGroup({ name, zoneId: zone, capacityM3: text('capacityM3', [positive], null) });
      case 'pumps':
        return new FormGroup({ name, zoneId: zone });
      case 'sensors':
        return new FormGroup({
          name,
          type: text('type', [Validators.required], null),
          zoneId: zone,
          assetType: text('assetType', [Validators.required], null),
          assetId: text('assetId', [Validators.required], null),
        });
    }
  }

  /** Synchronise les signaux utilisés par le sélecteur d'actifs avec les champs du capteur. */
  private watch(): void {
    const c = this.form.controls;
    this.zoneSel.set((c['zoneId']?.value as number | null | undefined) ?? null);
    this.kindSel.set((c['assetType']?.value as AssetKind | null | undefined) ?? null);
    if (this.category() !== 'sensors') {
      return;
    }
    const dropInvalidAsset = () => {
      if (c['assetId'].value !== null && !this.assetOptions().some((o) => o.id === c['assetId'].value)) {
        c['assetId'].setValue(null);
      }
    };
    c['zoneId'].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => {
      this.zoneSel.set(v as number | null);
      dropInvalidAsset();
    });
    c['assetType'].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => {
      this.kindSel.set(v as AssetKind | null);
      dropInvalidAsset();
    });
    c['type'].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => {
      // Suggestion seulement : tant que l'utilisateur n'a pas choisi lui-même le type d'actif.
      if (v && c['assetType'].pristine) {
        c['assetType'].setValue(SUGGESTED_KIND[v as SensorType]);
      }
    });
  }

  private toValue(): AssetFormValue {
    const v = this.form.getRawValue();
    const str = (x: FieldValue | undefined): string => String(x ?? '').trim();
    const num = (x: FieldValue | undefined): number | null => (x === null || x === undefined || x === '' ? null : Number(x));
    switch (this.category()) {
      case 'zones':
        return { category: 'zones', body: { name: str(v['name']), description: str(v['description']) || null } };
      case 'pipes':
        return {
          category: 'pipes',
          body: { name: str(v['name']), zoneId: Number(v['zoneId']), lengthM: num(v['lengthM']), diameterMm: num(v['diameterMm']) },
        };
      case 'tanks':
        return { category: 'tanks', body: { name: str(v['name']), zoneId: Number(v['zoneId']), capacityM3: num(v['capacityM3']) } };
      case 'pumps':
        return { category: 'pumps', body: { name: str(v['name']), zoneId: Number(v['zoneId']) } };
      case 'sensors':
        return {
          category: 'sensors',
          body: {
            name: str(v['name']),
            type: v['type'] as SensorType,
            zoneId: Number(v['zoneId']),
            assetType: v['assetType'] as AssetKind,
            assetId: Number(v['assetId']),
          },
        };
    }
  }
}
