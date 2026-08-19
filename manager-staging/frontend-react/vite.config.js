import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins:[react()],
  base:'/__PM_BASE__/react-dist/',
  build:{
    outDir:'../public/react-dist',
    emptyOutDir:true,
    assetsDir:'assets',
    sourcemap:false,
    manifest:true,
    target:'es2022',
  },
})
