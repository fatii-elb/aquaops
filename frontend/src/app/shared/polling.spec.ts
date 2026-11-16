import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { pollEvery } from './polling';

describe('pollEvery', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('émet tout de suite, puis à chaque intervalle', () => {
    let ticks = 0;
    const sub = pollEvery(10_000, () => true).subscribe(() => ticks++);
    expect(ticks).toBe(1);
    vi.advanceTimersByTime(9_999);
    expect(ticks).toBe(1);
    vi.advanceTimersByTime(1);
    expect(ticks).toBe(2);
    vi.advanceTimersByTime(20_000);
    expect(ticks).toBe(4);
    sub.unsubscribe();
  });

  it("n'émet pas pendant que l'onglet est masqué, et reprend ensuite", () => {
    let visible = true;
    let ticks = 0;
    const sub = pollEvery(1000, () => visible).subscribe(() => ticks++);
    visible = false;
    vi.advanceTimersByTime(3000);
    expect(ticks).toBe(1);
    visible = true;
    vi.advanceTimersByTime(1000);
    expect(ticks).toBe(2);
    sub.unsubscribe();
  });

  it('cesse d’émettre après désabonnement', () => {
    let ticks = 0;
    pollEvery(1000, () => true).subscribe(() => ticks++).unsubscribe();
    vi.advanceTimersByTime(5000);
    expect(ticks).toBe(1);
  });
});
