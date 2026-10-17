import { expect, test } from "@playwright/test";
import ExcelJS from "exceljs";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Username").fill("tester");
  await page.getByLabel("Password").fill("test-password");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/dashboard/);
});

test("creates independent expense, obligation, and account records", async ({
  page,
}, testInfo) => {
  const suffix = `${testInfo.project.name}-${Date.now()}`;

  await page.goto("/categories");
  await page.getByLabel("სახელი").last().fill(`E2E Category ${suffix}`);
  await page.getByRole("button", { name: "დამატება" }).click();

  await page.goto("/expenses");
  await page.getByLabel("თანხა").first().fill("12.50");
  await page.getByLabel("მიმღები").fill(`E2E Merchant ${suffix}`);
  await page.getByRole("button", { name: "დამატება" }).first().click();
  await expect(page.getByText(`E2E Merchant ${suffix}`)).toBeVisible();

  await page.goto("/obligations");
  await page.getByText("მხარის დამატება").click();
  await page
    .getByLabel("სახელი", { exact: true })
    .fill(`E2E Company ${suffix}`);
  await page.getByRole("button", { name: "დამატება" }).last().click();
  await expect(
    page.locator("strong").filter({ hasText: `E2E Company ${suffix}` }),
  ).toBeVisible();

  await page.goto("/accounts");
  await page.getByLabel("სახელი").fill(`E2E Checking ${suffix}`);
  await page.getByLabel("საწყისი ნაშთი").fill("500");
  await page.getByRole("button", { name: "დამატება" }).first().click();
  await expect(
    page.getByRole("heading", { name: `E2E Checking ${suffix}` }),
  ).toBeVisible();
});

test("previews and commits an XLDR-style workbook", async ({ page }, testInfo) => {
  const suffix = `${testInfo.project.name}-${Date.now()}`;
  const workbook = new ExcelJS.Workbook();
  const entries = workbook.addWorksheet("ჩანაწერები");
  entries.addRow([
    "კოდი",
    "ზეკატეგორია",
    "კატეგორია",
    "ქვეკატეგორია",
    "რაოდენობა",
    "შენიშვნა",
    "თარიღი",
    "მიმღები",
    "სპეცკატეგორია",
  ]);
  entries.addRow([
    `E2E-${suffix}`,
    "",
    "",
    "",
    -18.5,
    suffix,
    new Date("2026-06-14T00:00:00Z"),
    `Imported Merchant ${suffix}`,
    "e2e",
  ]);
  const dictionary = workbook.addWorksheet("ლექსიკონი");
  dictionary.addRow(["კოდი", "ზეკატეგორია", "კატეგორია", "ქვეკატეგორია"]);
  dictionary.addRow([
    `E2E-${suffix}`,
    `E2E Root ${suffix}`,
    `E2E Child ${suffix}`,
    "",
  ]);
  const buffer = Buffer.from(await workbook.xlsx.writeBuffer());

  await page.goto("/imports");
  await page.getByLabel("Excel ფაილი").setInputFiles({
    name: `e2e-${suffix}.xlsm`,
    mimeType: "application/vnd.ms-excel.sheet.macroEnabled.12",
    buffer,
  });
  await page.getByRole("button", { name: "წაკითხვა და გადახედვა" }).click();
  await expect(page.getByRole("heading", { name: `e2e-${suffix}.xlsm` })).toBeVisible();
  await page.getByRole("button", { name: "1 ჩანაწერის დადასტურება" }).click();
  await expect(page.getByText("იმპორტი დასრულებულია.")).toBeVisible();

  await page.goto("/expenses");
  await expect(page.getByText(`Imported Merchant ${suffix}`)).toBeVisible();
  await page.goto("/dashboard");
  const branch = page.getByRole("button", { name: `Toggle E2E Root ${suffix}` });
  await expect(branch).toHaveAttribute("aria-expanded", "false");
  await expect(async () => {
    if ((await branch.getAttribute("aria-expanded")) !== "true") {
      await branch.click();
    }
    await expect(branch).toHaveAttribute("aria-expanded", "true", {
      timeout: 1_000,
    });
  }).toPass({ timeout: 10_000 });
});
