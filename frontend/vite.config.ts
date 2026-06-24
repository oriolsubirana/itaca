import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: "autoUpdate",
      workbox: {
        // The SPA navigation fallback (serve index.html) must NOT swallow the backend's auth
        // endpoints — those have to reach the server: the OAuth redirect to Google, the login
        // callback, logout and the API. Without this denylist the service worker intercepts
        // /oauth2/authorization/google and returns the SPA, so login silently does nothing.
        navigateFallbackDenylist: [/^\/api\//, /^\/oauth2\//, /^\/login\//, /^\/logout$/, /^\/actuator\//],
      },
      manifest: {
        name: "Ítaca",
        short_name: "Ítaca",
        description: "Dashboard personal: salud, entrenamiento y finanzas",
        lang: "es",
        display: "standalone",
        background_color: "#faf9f7",
        theme_color: "#faf9f7",
        icons: [
          { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
          {
            src: "/icons/icon-maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
