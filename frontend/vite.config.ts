import { fileURLToPath, URL } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const src = (path: string) => fileURLToPath(new URL(`./src/${path}`, import.meta.url));

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": src(""),
      // The components were written for Next.js; these shims map its Link and
      // navigation hooks onto React Router so the component code is unchanged.
      "next/link": src("shims/next-link.tsx"),
      "next/navigation": src("shims/next-navigation.ts"),
    },
  },
  server: {
    // `pnpm dev` talks to the Spring Boot backend on :8080.
    proxy: { "/api": "http://localhost:8080" },
  },
  preview: {
    proxy: { "/api": "http://localhost:8080" },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.{ts,tsx}"],
    exclude: ["e2e/**", "node_modules/**"],
  },
});
