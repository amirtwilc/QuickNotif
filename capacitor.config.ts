import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'app.amir.quicknotif',
  appName: 'Quick Notif',
  webDir: 'dist',
  server: {
    androidScheme: "https"
  },
  plugins: {
    LocalNotifications: {
      smallIcon: "ic_stat_notification",
      iconColor: "#6366F1"
    }
  },
  android: {
    allowMixedContent: true,
    overrideUserAgent: "Quick Notif App",
    appendUserAgent: "Quick Notif",
    webContentsDebuggingEnabled: true
  }
};

export default config;