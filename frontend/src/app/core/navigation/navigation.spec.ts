import { NAVIGATION, navigationFor } from './navigation';

describe('navigationFor', () => {
  const paths = (sections: ReturnType<typeof navigationFor>) =>
    sections.flatMap((section) => section.items.map((item) => item.path));

  it('gives each role only its own entries', () => {
    const all = new Set([
      'dashboard',
      'tickets',
      'interventions',
      'equipment',
      'reports',
      'knowledge',
      'assistant',
      'users',
      'settings',
      'audit',
    ]);

    expect(paths(navigationFor('EMPLOYEE', all, false))).toEqual([
      'dashboard',
      'tickets',
      'equipment',
      'knowledge',
      'assistant',
    ]);
    expect(paths(navigationFor('TECHNICIAN', all, false))).not.toContain('users');
    expect(paths(navigationFor('ADMIN', all, false))).toEqual(
      expect.arrayContaining(['users', 'settings', 'audit']),
    );
  });

  it('hides pages that have no route yet, and their empty sections, outside development', () => {
    const sections = navigationFor('ADMIN', new Set(['dashboard', 'tickets']), false);

    expect(paths(sections)).toEqual(['dashboard', 'tickets']);
    expect(sections.map((section) => section.title)).toEqual([null]);
  });

  it('keeps them, flagged unavailable, in development', () => {
    const items = navigationFor('ADMIN', new Set(['tickets']), true).flatMap((s) => s.items);

    expect(items.length).toBe(NAVIGATION.ADMIN.flatMap((s) => s.items).length);
    expect(items.find((item) => item.path === 'tickets')?.available).toBe(true);
    expect(items.find((item) => item.path === 'audit')?.available).toBe(false);
  });
});
