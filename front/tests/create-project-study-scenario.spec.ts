import { test, expect } from '@playwright/test';
import { PlaywrightHomePage } from './home-page-model';

test.describe('Testing if all mandatory elements simulation configuration are loaded in operationnal studies app', () => {
  // Declare the necessary variable for the test
  let playwrightHomePage: PlaywrightHomePage;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();

    playwrightHomePage = new PlaywrightHomePage(page);
    await playwrightHomePage.goToHomePage();

    await playwrightHomePage.goToOperationalStudiesPage();
  });

  test('RollingStockSelector is displayed', async () => {
    await playwrightHomePage.page.locator('.projects-list-project-card.empty').click();
    await expect(playwrightHomePage.page.locator('.project-edition-modal')).toBeVisible();
  });
});
