import { Component, computed, input, output } from '@angular/core';

/** Pied de tableau : plage affichée, taille de page, page précédente et suivante. La page est comptée à partir de 0. */
@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.html',
})
export class Pagination {
  readonly page = input(0);
  readonly size = input(20);
  readonly total = input(0);
  readonly sizes = input<number[]>([10, 20, 50]);

  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();

  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));
  protected readonly from = computed(() => (this.total() === 0 ? 0 : this.page() * this.size() + 1));
  protected readonly to = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));
  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.pageCount());

  protected previous(): void {
    if (this.hasPrevious()) {
      this.pageChange.emit(this.page() - 1);
    }
  }

  protected next(): void {
    if (this.hasNext()) {
      this.pageChange.emit(this.page() + 1);
    }
  }

  protected changeSize(value: string): void {
    this.sizeChange.emit(Number(value));
  }
}
