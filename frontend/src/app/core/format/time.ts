// Pure date / duration formatting (French), unit-tested. `now` is always passed in, never read here,
// so results are deterministic and components refresh them from a shared clock.

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** Until the API has answered (and in tests): the default of the backend's APP_TIMEZONE. */
export const DEFAULT_TIME_ZONE = 'Europe/Paris';

let zone = DEFAULT_TIME_ZONE;

const RELATIVE = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' });
let DATE = dateFormat();
let DATE_TIME = dateTimeFormat();

function dateFormat(): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: zone,
  });
}

function dateTimeFormat(): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: zone,
  });
}

/**
 * Dates are shown in the company's time zone, whatever the browser's: SLA deadlines follow the company's business
 * hours, and everyone must read the same deadline. The zone comes from the API (APP_TIMEZONE) at start-up.
 */
export function companyTimeZone(): string {
  return zone;
}

/** Called once at start-up with the API's zone; an unknown zone name is ignored (the default stays). */
export function setCompanyTimeZone(timeZone: string): void {
  try {
    new Intl.DateTimeFormat('fr-FR', { timeZone });
  } catch {
    return;
  }
  zone = timeZone;
  DATE = dateFormat();
  DATE_TIME = dateTimeFormat();
}

/** "moins d'1 min", "35 min", "1 h 24", "2 j 3 h". Negative durations are formatted by absolute value. */
export function formatDuration(ms: number): string {
  const abs = Math.abs(ms);
  if (abs < MINUTE) {
    return "moins d'1 min";
  }
  if (abs < HOUR) {
    return `${Math.floor(abs / MINUTE)} min`;
  }
  if (abs < DAY) {
    const hours = Math.floor(abs / HOUR);
    const minutes = Math.floor((abs % HOUR) / MINUTE);
    return minutes === 0 ? `${hours} h` : `${hours} h ${String(minutes).padStart(2, '0')}`;
  }
  const days = Math.floor(abs / DAY);
  const hours = Math.floor((abs % DAY) / HOUR);
  return hours === 0 ? `${days} j` : `${days} j ${hours} h`;
}

/** "à l'instant", "il y a 5 minutes", "hier", "dans 3 heures"; beyond a week, the date itself. */
export function formatRelative(date: Date | string, now: number): string {
  const time = typeof date === 'string' ? Date.parse(date) : date.getTime();
  const diff = time - now;
  const abs = Math.abs(diff);
  if (abs < MINUTE) {
    return "à l'instant";
  }
  if (abs < HOUR) {
    return RELATIVE.format(Math.round(diff / MINUTE), 'minute');
  }
  if (abs < DAY) {
    return RELATIVE.format(Math.round(diff / HOUR), 'hour');
  }
  if (abs < 7 * DAY) {
    return RELATIVE.format(Math.round(diff / DAY), 'day');
  }
  return DATE.format(time);
}

/** "02/11/2026 14:35": exact date shown in tooltips next to relative times. */
export function formatDateTime(date: Date | string): string {
  return DATE_TIME.format(typeof date === 'string' ? Date.parse(date) : date.getTime());
}

/** "Amina Benali" → "AB"; one word → its first two letters. */
export function initials(fullName: string): string {
  const words = fullName.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}
