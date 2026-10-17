import { defineConfig, devices } from "@playwright/test";

// bcrypt of "test-password", the same fixture the Next.js app's e2e tests used.
const passwordHash = "$2b$12$YMNe4qdD/2SVVdy680Rj5ud/d90izJrxvPXz9Ezr/bcUHhkLj1Cqq";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://127.0.0.1:3100",
    trace: "on-first-retry",
  },
  webServer: [
    {
      // The Spring Boot app on embedded PostgreSQL (backend DevServer).
      command: `${process.platform === "win32" ? ".\\mvnw.cmd" : "./mvnw"} -B -q spring-boot:test-run`,
      cwd: "../backend",
      url: "http://127.0.0.1:8080/api/health",
      reuseExistingServer: !process.env.CI,
      timeout: 300_000,
      env: {
        ...process.env,
        APP_USERNAME: "tester",
        APP_PASSWORD_HASH: passwordHash,
        SESSION_SECRET: "playwright-session-secret-longer-than-thirty-two-chars",
      },
    },
    {
      // The production build: the dev server reloads pages when it discovers dependencies.
      command: "pnpm build && pnpm preview --port 3100 --strictPort --host 127.0.0.1",
      url: "http://127.0.0.1:3100/login",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile", use: { ...devices["Pixel 7"] } },
  ],
});
