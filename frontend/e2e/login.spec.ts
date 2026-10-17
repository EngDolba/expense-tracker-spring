import { expect, test } from "@playwright/test";

test("protects the app and supports bilingual navigation", async ({ page }) => {
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login/);

  await page.getByLabel("Username").fill("tester");
  await page.getByLabel("Password").fill("test-password");
  await page.getByRole("button", { name: /sign in/i }).click();

  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByRole("heading", { name: "მიმოხილვა" })).toBeVisible();
  await page.getByRole("button", { name: "Change language" }).click();
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
});
