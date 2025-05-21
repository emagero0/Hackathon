import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"), // Absolute path to src directory
    },
  },
  server: {
    proxy: {
      // Proxy API requests starting with /api to the backend server
      '/api': {
        target: 'http://localhost:8081', // Your backend server address (running on port 8081)
        changeOrigin: true, // Needed for virtual hosted sites
        secure: false,      // Optional: Set to false if backend is not using HTTPS
        // Optional: Rewrite path if needed (e.g., remove /api prefix)
        // rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  // Removed the extra closing brace from here
});