import { firstValueFrom } from 'rxjs';
import { MockAuditDataSource } from './mock-audit-data-source';

describe('MockAuditDataSource', () => {
  const source = new MockAuditDataSource();

  it('renvoie la première page, les plus récentes d\u2019abord', async () => {
    const page = await firstValueFrom(source.search({ page: 0, size: 20 }));
    expect(page.content).toHaveLength(20);
    expect(page.totalElements).toBe(57);
    expect(page.totalPages).toBe(3);
    const dates = page.content.map((e) => Date.parse(e.occurredAt));
    expect([...dates].sort((a, b) => b - a)).toEqual(dates);
  });

  it('la dernière page contient le reste', async () => {
    const page = await firstValueFrom(source.search({ page: 2, size: 20 }));
    expect(page.content).toHaveLength(17);
  });

  it('filtre par utilisateur sans tenir compte de la casse', async () => {
    const page = await firstValueFrom(source.search({ page: 0, size: 100, actor: 'TEST-TECH' }));
    expect(page.totalElements).toBeGreaterThan(0);
    expect(page.content.every((e) => e.actor === 'test-technicien')).toBe(true);
  });

  it('filtre par type d\u2019action', async () => {
    const page = await firstValueFrom(source.search({ page: 0, size: 100, action: 'DELETE' }));
    expect(page.totalElements).toBeGreaterThan(0);
    expect(page.content.every((e) => e.action === 'DELETE')).toBe(true);
  });

  it('filtre par période, bornes incluses', async () => {
    const all = await firstValueFrom(source.search({ page: 0, size: 100 }));
    const from = all.content[10].occurredAt;
    const to = all.content[5].occurredAt;
    const page = await firstValueFrom(source.search({ page: 0, size: 100, from, to }));
    expect(page.totalElements).toBe(6);
  });

  it('une période sans entrée renvoie une page vide', async () => {
    const page = await firstValueFrom(source.search({ page: 0, size: 20, from: '2030-01-01T00:00:00Z' }));
    expect(page.content).toEqual([]);
    expect(page.totalElements).toBe(0);
    expect(page.totalPages).toBe(0);
  });

  it('liste les types d\u2019action présents, sans doublon', async () => {
    const actions = await firstValueFrom(source.actions());
    expect(actions).toContain('CREATE');
    expect(new Set(actions).size).toBe(actions.length);
  });
});
