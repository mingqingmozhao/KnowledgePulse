import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.knowledgepulse.app',
  appName: 'KnowledgePulse',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  }
}

export default config
