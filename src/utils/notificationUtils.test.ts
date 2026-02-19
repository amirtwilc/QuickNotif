import { describe, it, expect } from 'vitest';
import { toNumericId } from './notificationUtils';

describe('toNumericId', () => {
  describe('range constraints', () => {
    const samples = [
      '',
      'a',
      'hello',
      'notification_1234567890_abc123xyz',
      'x'.repeat(10_000),
    ];

    it.each(samples)('returns value >= 1 for input %j', (input) => {
      expect(toNumericId(input)).toBeGreaterThanOrEqual(1);
    });

    it.each(samples)('returns value <= 2147483646 for input %j', (input) => {
      expect(toNumericId(input)).toBeLessThanOrEqual(2147483646);
    });

    it.each(samples)('returns integer for input %j', (input) => {
      expect(Number.isInteger(toNumericId(input))).toBe(true);
    });

    it('never returns 0 (Android requires non-zero IDs)', () => {
      // Verify via the mathematical guarantee: result is always % 2147483646 + 1
      expect(toNumericId('')).not.toBe(0);
      expect(toNumericId('a')).not.toBe(0);
    });
  });

  describe('determinism', () => {
    it('returns same value for same input every time', () => {
      const id = 'notification_1718000000000_x9f3k2q';
      expect(toNumericId(id)).toBe(toNumericId(id));
    });

    it('returns different values for different inputs', () => {
      expect(toNumericId('hello')).not.toBe(toNumericId('world'));
      expect(toNumericId('a')).not.toBe(toNumericId('b'));
    });
  });

  describe('golden values (must match Java generateNumericId)', () => {
    it('empty string => 5382', () => {
      expect(toNumericId('')).toBe(5382);
    });

    it('"a" => 177605', () => {
      expect(toNumericId('a')).toBe(177605);
    });

    it('"hello" => 178056680', () => {
      expect(toNumericId('hello')).toBe(178056680);
    });

    it('"notification_1234567890_abc123xyz" => 1603438271', () => {
      expect(toNumericId('notification_1234567890_abc123xyz')).toBe(1603438271);
    });
  });

  describe('edge cases', () => {
    it('handles single character without throwing', () => {
      expect(() => toNumericId('z')).not.toThrow();
    });

    it('handles 10,000 character string without overflow or throw', () => {
      const longStr = 'x'.repeat(10_000);
      expect(() => toNumericId(longStr)).not.toThrow();
      const result = toNumericId(longStr);
      expect(result).toBeGreaterThanOrEqual(1);
      expect(result).toBeLessThanOrEqual(2147483646);
    });
  });
});
