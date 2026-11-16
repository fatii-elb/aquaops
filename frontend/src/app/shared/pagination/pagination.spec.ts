import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Pagination } from './pagination';

function render(page: number, size: number, total: number) {
  const fixture = TestBed.createComponent(Pagination);
  fixture.componentRef.setInput('page', page);
  fixture.componentRef.setInput('size', size);
  fixture.componentRef.setInput('total', total);
  const pages: number[] = [];
  const sizes: number[] = [];
  fixture.componentInstance.pageChange.subscribe((p) => pages.push(p));
  fixture.componentInstance.sizeChange.subscribe((s) => sizes.push(s));
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  return { el, pages, sizes, info: () => el.querySelector('[data-testid="pagination-info"]')!.textContent!.trim() };
}

describe('Pagination', () => {
  it('affiche la plage et le nombre de pages', () => {
    const { el, info } = render(0, 20, 45);
    expect(info()).toBe('1–20 sur 45');
    expect(el.textContent).toContain('Page 1 / 3');
  });

  it('dernière page : plage tronquée, suivant désactivé', () => {
    const { el, info } = render(2, 20, 45);
    expect(info()).toBe('41–45 sur 45');
    expect((el.querySelector('[data-testid="pagination-next"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('liste vide : 0–0 sur 0, rien à parcourir', () => {
    const { el, info } = render(0, 20, 0);
    expect(info()).toBe('0–0 sur 0');
    expect((el.querySelector('[data-testid="pagination-prev"]') as HTMLButtonElement).disabled).toBe(true);
    expect((el.querySelector('[data-testid="pagination-next"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('émet la page demandée (comptée depuis 0)', () => {
    const { el, pages } = render(1, 20, 100);
    (el.querySelector('[data-testid="pagination-next"]') as HTMLButtonElement).click();
    (el.querySelector('[data-testid="pagination-prev"]') as HTMLButtonElement).click();
    expect(pages).toEqual([2, 0]);
  });

  it('émet la nouvelle taille de page', () => {
    const { el, sizes } = render(0, 20, 100);
    const select = el.querySelector('select') as HTMLSelectElement;
    select.value = '50';
    select.dispatchEvent(new Event('change'));
    expect(sizes).toEqual([50]);
  });
});
