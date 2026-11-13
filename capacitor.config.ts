import type { CapacitorConfig } from '@capacitor/cli';
const config: CapacitorConfig = {
  appId: 'com.mist.player',
  appName: 'Mist',
  webDir: 'www',
  backgroundColor: '#F7F6F2',
  server: { androidScheme: 'https' },
  android: { allowMixedContent: true },
  plugins: { SplashScreen: { launchShowDuration: 1200, backgroundColor: '#F7F6F2', showSpinner: false } },
};
export default config;
