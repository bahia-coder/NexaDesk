// Converts a wall-clock date-time typed in a form ("2026-11-02T18:30", as a datetime-local input gives it) into an
// instant, reading it in a given time zone (the company's), not in the browser's. Pure, unit-tested.

function offsetMinutes(instant: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(instant);
  const value = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const asUtc = Date.UTC(
    value('year'),
    value('month') - 1,
    value('day'),
    value('hour'),
    value('minute'),
    value('second'),
  );
  return Math.round((asUtc - Math.floor(instant / 1000) * 1000) / 60_000);
}

/** "2026-11-02T18:30" in Europe/Paris → "2026-11-02T17:30:00.000Z". Null for an empty or malformed value. */
export function zonedToInstant(local: string, timeZone: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(local);
  if (!match) {
    return null;
  }
  const [, y, mo, d, h, mi] = match.map(Number);
  const wall = Date.UTC(y, mo - 1, d, h, mi);
  // The zone's offset at that moment; computed twice so a date next to a DST change gets the right one.
  let instant = wall - offsetMinutes(wall, timeZone) * 60_000;
  instant = wall - offsetMinutes(instant, timeZone) * 60_000;
  return new Date(instant).toISOString();
}

/** The current wall-clock time in a zone, as a datetime-local value ("2026-11-02T18:30"). */
export function nowInZone(now: number, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).formatToParts(now);
  const value = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  return `${value('year')}-${value('month')}-${value('day')}T${value('hour')}:${value('minute')}`;
}
