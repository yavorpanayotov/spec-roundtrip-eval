// Round-trip product-fidelity probe, as run on 2026-07-08 against both rebuilds.
// Drives the full claim lifecycle across all four roles and performs the KD-1
// URL probe (can an employee open a foreign claim by URL?).
// Usage: node probe.js <aiup|allium>   (needs `npm i playwright` + chromium)
// Start the target first: AIUP rebuild on :8082 (spring-boot:run
// -Dspring-boot.run.arguments=--server.port=8082), Allium rebuild on :8090
// (jetty:run -Djetty.http.port=8090).
const { chromium } = require('playwright');
const SHOTS = __dirname + '/screenshots';
require('fs').mkdirSync(SHOTS, { recursive: true });

const CONFIGS = {
  aiup: {
    base: 'http://localhost:8082',
    emp1: 'Emma', emp2: 'Erik', manager: 'Mona', finance: 'Frank',
    urlProbeVisible: false, // reviewers-only policy: employee must NOT see foreign claim by URL
  },
  allium: {
    base: 'http://localhost:8090',
    emp1: 'Alice', emp2: 'Ben', manager: 'Mara', finance: 'Frank',
    urlProbeVisible: true, // as-specified: any signed-in user can open any claim by URL
  },
};
const which = process.argv[2];
const cfg = CONFIGS[which];
const run = Date.now().toString().slice(-5);
const TITLE = `Roundtrip probe ${run}`;

(async () => {
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
  page.setDefaultTimeout(15000);
  const jsErrors = [];
  page.on('pageerror', e => jsErrors.push(String(e)));
  const shot = async name => { await page.screenshot({ path: `${SHOTS}/${which}-${name}.png`, fullPage: true }); console.log('SHOT', name); };

  async function signIn(name) {
    await page.goto(cfg.base + '/login');
    if (!(await page.locator('vaadin-combo-box').count())) await page.goto(cfg.base + '/');
    const combo = page.locator('vaadin-combo-box input').first();
    await combo.click();
    await combo.fill(name);
    await page.locator('vaadin-combo-box-item').first().click();
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.getByRole('button', { name: /new claim/i }).waitFor();
    console.log('OK signed in as', name);
  }
  async function signOut() {
    await page.getByRole('button', { name: /sign out|log ?out/i }).click();
    await page.locator('vaadin-combo-box input').first().waitFor();
  }

  try {
    // 1. Employee 1: create → item → submit
    await signIn(cfg.emp1);
    await page.getByRole('button', { name: /new claim/i }).click();
    await page.locator('vaadin-dialog-overlay input, vaadin-text-field input').first().fill(TITLE);
    await page.getByRole('button', { name: /^create$|^ok$|^save$/i }).click();
    await page.getByText(TITLE).first().waitFor();
    await page.getByRole('button', { name: /add item/i }).click();
    const catCombo = page.locator('vaadin-dialog-overlay vaadin-combo-box input, vaadin-dialog-overlay vaadin-select').first();
    await catCombo.click();
    await page.locator('vaadin-combo-box-item, vaadin-select-item').first().click();
    await page.getByLabel(/description/i).fill('Roundtrip item');
    await page.getByLabel(/amount/i).fill('120.50');
    const dateField = page.getByLabel(/date/i);
    await dateField.fill('01/06/2026'); // past date in both day-first and month-first locales
    await dateField.press('Enter');
    await page.getByLabel(/receipt/i).check();
    await page.getByRole('button', { name: /^save$|^add$|^ok$/i }).click();
    await page.getByText('Roundtrip item').waitFor();
    console.log('OK item added');
    await page.getByRole('button', { name: /^submit/i }).click();
    await page.getByText(/submitted/i).first().waitFor();
    const claimUrl = page.url();
    console.log('OK submitted; claim url:', claimUrl);
    await shot('01-submitted');

    // 2. Employee 2: list must not show it; URL probe (KD-1)
    await signOut();
    await signIn(cfg.emp2);
    const inList = await page.getByText(TITLE).count();
    console.log(inList === 0 ? 'OK list scoped: emp2 does not see the claim' : 'VIOLATION: emp2 sees foreign claim in list');
    await page.goto(claimUrl);
    await page.waitForTimeout(2500);
    const detailVisible = (await page.getByText(TITLE).count()) > 0;
    console.log(`URL-PROBE (KD-1): foreign claim detail ${detailVisible ? 'VISIBLE' : 'HIDDEN'} to employee (expected: ${cfg.urlProbeVisible ? 'VISIBLE' : 'HIDDEN'})`);
    await shot('02-url-probe');

    // 3. Manager approves
    await signOut();
    await signIn(cfg.manager);
    await page.getByText(TITLE).first().click();
    await page.getByRole('button', { name: /approve/i }).click();
    await page.getByText(/approved/i).first().waitFor();
    console.log('OK manager approved');
    await shot('03-approved');

    // 4. Finance reimburses; history visible
    await signOut();
    await signIn(cfg.finance);
    await page.getByText(TITLE).first().click();
    await page.getByRole('button', { name: /reimburse/i }).click();
    await page.getByText(/reimbursed/i).first().waitFor();
    const history = await page.locator('vaadin-grid-cell-content').allTextContents();
    const acts = ['submitted', 'approved', 'reimbursed'].filter(a => history.some(t => t.toLowerCase().includes(a)));
    console.log(`OK reimbursed; history shows: ${acts.join(', ')}`);
    await shot('04-reimbursed');

    console.log(jsErrors.length ? 'JS ERRORS: ' + JSON.stringify(jsErrors) : 'OK no JS console errors');
    console.log('PROBE COMPLETE for', which);
  } catch (e) {
    console.error('FAILED:', e.message.split('\n')[0]);
    await shot('failure');
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
