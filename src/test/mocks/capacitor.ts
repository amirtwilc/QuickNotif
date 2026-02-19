import { vi } from 'vitest';

export const mockLocalNotifications = {
  schedule: vi.fn().mockResolvedValue({}),
  cancel: vi.fn().mockResolvedValue({}),
  getPending: vi.fn().mockResolvedValue({ notifications: [] }),
  checkPermissions: vi.fn().mockResolvedValue({ display: 'granted' }),
  requestPermissions: vi.fn().mockResolvedValue({ display: 'granted' }),
  createChannel: vi.fn().mockResolvedValue({}),
};

export const mockPreferences = {
  get: vi.fn().mockResolvedValue({ value: null }),
  set: vi.fn().mockResolvedValue({}),
  remove: vi.fn().mockResolvedValue({}),
};

export const mockCapacitorCore = {
  isNativePlatform: vi.fn(() => false),
  getPlatform: vi.fn(() => 'web'),
};

export const mockApp = {
  addListener: vi.fn().mockResolvedValue({ remove: vi.fn() }),
};

export const mockFilesystem = {
  readFile: vi.fn().mockResolvedValue({ data: '' }),
  writeFile: vi.fn().mockResolvedValue({}),
  getUri: vi.fn().mockResolvedValue({ uri: '' }),
};
