import {
  DEFAULT_TIME_ZONE,
  companyTimeZone,
  formatDateTime,
  formatDuration,
  formatRelative,
  initials,
  setCompanyTimeZone,
} from './time';

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

describe('formatDuration', () => {
  it('formats minutes, hours and days, ignoring the sign', () => {
    expect(formatDuration(30_000)).toBe("moins d'1 min");
    expect(formatDuration(35 * MIN)).toBe('35 min');
    expect(formatDuration(-35 * MIN)).toBe('35 min');
    expect(formatDuration(HOUR + 4 * MIN)).toBe('1 h 04');
    expect(formatDuration(3 * HOUR)).toBe('3 h');
    expect(formatDuration(2 * DAY + 3 * HOUR + 10 * MIN)).toBe('2 j 3 h');
    expect(formatDuration(5 * DAY)).toBe('5 j');
  });
});

describe('formatRelative', () => {
  const now = Date.parse('2026-11-02T12:00:00Z');

  it('uses natural French wording up to a week, then the date', () => {
    expect(formatRelative(new Date(now - 20_000), now)).toBe("à l'instant");
    expect(formatRelative(new Date(now - 5 * MIN), now)).toBe('il y a 5 minutes');
    expect(formatRelative(new Date(now + 3 * HOUR), now)).toBe('dans 3 heures');
    expect(formatRelative(new Date(now - DAY), now)).toBe('hier');
    expect(formatRelative('2026-10-01T12:00:00Z', now)).toBe('01/10/2026');
  });
});

describe('formatDateTime', () => {
  it('shows the company time (Europe/Paris), whatever the browser time zone', () => {
    expect(formatDateTime('2026-11-02T19:32:00Z')).toBe('02/11/2026 20:32');
    expect(formatDateTime('2026-07-01T06:00:00Z')).toBe('01/07/2026 08:00');
  });

  it('follows the zone the API configures, and ignores an unknown one', () => {
    try {
      setCompanyTimeZone('Africa/Casablanca');
      expect(companyTimeZone()).toBe('Africa/Casablanca');
      expect(formatDateTime('2026-11-02T19:32:00Z')).toBe('02/11/2026 20:32');
      setCompanyTimeZone('America/New_York');
      expect(formatDateTime('2026-11-02T19:32:00Z')).toBe('02/11/2026 14:32');
      setCompanyTimeZone('Not/AZone');
      expect(companyTimeZone()).toBe('America/New_York');
    } finally {
      setCompanyTimeZone(DEFAULT_TIME_ZONE);
    }
  });
});

describe('initials', () => {
  it('takes the first and last word', () => {
    expect(initials('Amina Benali')).toBe('AB');
    expect(initials('  Jean  Pierre Martin ')).toBe('JM');
    expect(initials('sara')).toBe('SA');
    expect(initials('')).toBe('?');
  });
});
