import { expect, type Locator, type Page } from '@playwright/test';

import SimulationConfPage from './simulation-conf-page';

export default class StdcmPage extends SimulationConfPage {
  readonly missingParams: Locator;

  // Scenario Explorator
  private scenarioExplorerButton: Locator;

  readonly scenarioExplorerModal: Locator;

  private scenarioExplorerMinicards: Locator;

  // STDCM
  private getOriginTimeDelta: Locator;

  constructor(page: Page) {
    super(page);

    this.missingParams = page.locator('.missing-params');

    // Scenario Explorator
    this.scenarioExplorerButton = page.getByTestId('scenario-explorator');
    this.scenarioExplorerModal = page.locator('.scenario-explorator-modal');
    this.scenarioExplorerMinicards = page.locator('.minicard');

    // STDCM
    this.getOriginTimeDelta = page.locator('#osrd-config-time-origin').first();
  }

  async navigateToPage() {
    await this.page.goto('/stdcm/');
    await this.removeViteOverlay();
  }

  // Scenario Explorator
  async openScenarioExplorer() {
    await this.scenarioExplorerButton.click();
    await expect(this.scenarioExplorerModal).toBeVisible();
  }

  async selectMiniCard(itemName: string) {
    const miniCards = this.scenarioExplorerMinicards.getByText(itemName);
    await miniCards.first().click();
  }

  // STDCM
  async setOriginTime(digits: string) {
    const splittedDigit = digits.split('');
    await this.getOriginTimeDelta.focus();
    splittedDigit.forEach(async (digit) => {
      await this.page.keyboard.press(digit);
    });
  }
}
