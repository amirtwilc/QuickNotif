import '@testing-library/jest-dom';
import { vi, beforeEach } from 'vitest';

// Stub the window.Android bridge used by NotificationService
Object.defineProperty(window, 'Android', {
  writable: true,
  value: {
    isBatteryOptimized: vi.fn(() => false),
    openBatterySettings: vi.fn(),
    openAutoStartSettings: vi.fn(() => true),
    openAppSettings: vi.fn(),
    isAlarmScheduled: vi.fn(() => false),
    checkAllAlarms: vi.fn(() => '{}'),
    cancelAlarmManagerNotification: vi.fn(),
    refreshWidget: vi.fn(),
  },
});

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});
