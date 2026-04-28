import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  define: {
    global: 'globalThis'
  },
  envPrefix: ['VITE_', 'PUBLIC_'],
  plugins: [
    vue(),
    AutoImport({
      dts: resolve(__dirname, 'src/auto-imports.d.ts'),
      imports: ['vue', 'vue-router'],
      resolvers: [ElementPlusResolver()]
    }),
    Components({
      dts: resolve(__dirname, 'src/components.d.ts'),
      resolvers: [
        ElementPlusResolver({
          importStyle: 'css'
        })
      ]
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  optimizeDeps: {
    esbuildOptions: {
      define: {
        global: 'globalThis'
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return
          }

          if (id.includes('echarts')) {
            return 'vendor-echarts'
          }

          if (id.includes('vditor')) {
            return 'vendor-editor'
          }

          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'vendor-element'
          }

          if (id.includes('@stomp') || id.includes('sockjs-client')) {
            return 'vendor-realtime'
          }

          if (
            id.includes('vue-router') ||
            id.includes('pinia') ||
            id.includes('/vue/') ||
            id.includes('@vue')
          ) {
            return 'vendor-vue'
          }

          return 'vendor-misc'
        }
      }
    }
  },
  server: {
    port: 5173,
    allowedHosts: [
      'paramount-headwear-turbofan.ngrok-free.dev'
    ],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      },
      '/media': {
        target: 'http://localhost:8080/api/v1',
        changeOrigin: true
      }
    }
  }
})
