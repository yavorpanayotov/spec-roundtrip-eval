// Phase-0 UI verification of the reference app, as run on 2026-07-08.
// Drives every role through the full lifecycle: create/submit with validation
// checks (B01, B02, B11, B12), visibility (B20), approve (B18/B21/B08),
// reimburse + history (B19/B23), and the reject/resubmit loop (B17, B06).
// Usage: start the reference app (mvn spring-boot:run, port 8080), then
// node drive.js   (needs `npm i playwright` + chromium)
const { chromium } = require('playwright');
const SHOTS = __dirname + '/screenshots';
require('fs').mkdirSync(SHOTS, { recursive: true });

const run = Date.now().toString().slice(-5);
const TITLE1 = `Team offsite travel ${run}`;
const TITLE2 = `Client dinner ${run}`;

(async () => {
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
  page.setDefaultTimeout(15000);
  const jsErrors = [];
  page.on('pageerror', e => jsErrors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error') jsErrors.push(m.text()); });
  const shot = async name => { await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true }); console.log('SHOT', name); };

  async function signIn(filter) {
    await page.goto('http://localhost:8080/login');
    const combo = page.getByLabel('Sign in as');
    await combo.click();
    await combo.fill(filter);
    await page.locator('vaadin-combo-box-item').first().click();
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.getByRole('button', { name: 'New claim' }).waitFor();
    console.log('OK signed in as', filter);
  }
  async function signOut() {
    await page.getByRole('button', { name: 'Sign out' }).click();
    await page.getByLabel('Sign in as').waitFor();
  }
  async function newClaim(title) {
    await page.getByRole('button', { name: 'New claim' }).click();
    await page.getByLabel('Title').fill(title);
    await page.getByRole('button', { name: 'Create' }).click();
    await page.getByText('Status: draft').waitFor();
  }
  async function fillItemDialog(category, desc, amount, date, receipt) {
    await page.getByLabel('Category').click();
    await page.locator('vaadin-combo-box-item', { hasText: category }).click();
    await page.getByLabel('Description').fill(desc);
    await page.getByLabel('Amount (EUR)').fill(amount);
    const dateField = page.getByLabel('Expense date');
    await dateField.fill(date);
    await dateField.press('Enter'); // commit; Escape would revert the value and can close the dialog
    if (receipt) await page.getByLabel('Receipt attached').check();
    await page.getByRole('button', { name: 'Save' }).click();
  }

  try {
    // ---- Alice (employee): create, negative submit, add item, submit ----
    await signIn('Alice');
    await newClaim(TITLE1);
    console.log('OK B01 UI: new claim shows as draft');

    await page.getByRole('button', { name: 'Submit' }).click();
    await page.getByText('at least one expense item').waitFor();
    console.log('OK B02 UI: empty submit blocked with notification');
    await shot('01-alice-empty-submit-blocked');

    await page.getByRole('button', { name: /Add item/ }).click();
    // dates chosen to be in the past under both day-first and month-first locales
    await fillItemDialog('TRAVEL', 'Train to Zurich', '120.50', '01/06/2026', true);
    await page.getByText('Train to Zurich').waitFor();
    await page.getByText('Total: € 120.50').waitFor();
    console.log('OK B12 UI: item saved, total shown');
    await shot('02-alice-item-added');

    await page.getByRole('button', { name: 'Submit' }).click();
    await page.getByText('Status: submitted').waitFor();
    console.log('OK B03 UI: claim submitted');
    await shot('03-alice-submitted');

    // ---- Bob (employee): must NOT see Alice's claim ----
    await signOut();
    await signIn('Bob');
    if (await page.getByText(TITLE1).count() !== 0) throw new Error("B20 VIOLATION: Bob can see Alice's claim");
    console.log("OK B20 UI: Bob cannot see Alice's claim");
    await shot('04-bob-list');

    // ---- Carol (manager): sees submitted claim, approves ----
    await signOut();
    await signIn('Carol');
    await page.getByText(TITLE1).click();
    await page.getByText('Status: submitted').waitFor();
    if (await page.getByRole('button', { name: /Add item/ }).count() !== 0)
      throw new Error('B08 VIOLATION: manager sees item edit controls');
    console.log('OK B21/B08 UI: Carol sees the claim, no edit controls');
    await page.getByRole('button', { name: 'Approve' }).click();
    await page.getByText('Status: approved').waitFor();
    console.log('OK B18 UI: Carol approved');
    await shot('05-carol-approved');

    // ---- Dave (finance): reimburses, history complete ----
    await signOut();
    await signIn('Dave');
    await page.getByText(TITLE1).click();
    await page.getByRole('button', { name: 'Reimburse' }).click();
    await page.getByText('Status: reimbursed').waitFor();
    for (const action of ['submitted', 'approved', 'reimbursed']) {
      await page.locator('vaadin-grid-cell-content', { hasText: action }).first().waitFor();
    }
    console.log('OK B19/B23 UI: reimbursed, full history visible');
    await shot('06-dave-reimbursed-history');

    // ---- Rejection path: Bob submits, B11 receipt check, Erin rejects, Bob resubmits ----
    await signOut();
    await signIn('Bob');
    await newClaim(TITLE2);
    await page.getByRole('button', { name: /Add item/ }).click();
    await fillItemDialog('MEALS', 'Dinner with client', '80.00', '02/06/2026', false);
    await page.getByText('Dinner with client').waitFor();
    await page.getByRole('button', { name: 'Submit' }).click();
    await page.getByText('require a receipt').waitFor();
    console.log('OK B11 UI: submit blocked without receipt for item over 50');
    await shot('07-bob-receipt-blocked');

    await page.getByRole('button', { name: 'Edit', exact: true }).first().click();
    await page.getByLabel('Receipt attached').check();
    await page.getByRole('button', { name: 'Save' }).click();
    await page.getByRole('button', { name: 'Submit' }).click();
    await page.getByText('Status: submitted').waitFor();

    await signOut();
    await signIn('Erin');
    await page.getByText(TITLE2).click();
    await page.getByRole('button', { name: 'Reject…' }).click();
    await page.getByLabel('Reason').fill('Please attach the itemised bill');
    await page.getByRole('button', { name: 'Reject', exact: true }).click();
    await page.getByText('Status: rejected').waitFor();
    await page.getByText('Rejection reason: Please attach the itemised bill').waitFor();
    console.log('OK B17 UI: rejected with reason shown');
    await shot('08-erin-rejected');

    await signOut();
    await signIn('Bob');
    await page.getByText(TITLE2).click();
    await page.getByText('Status: rejected').waitFor();
    await page.getByRole('button', { name: 'Submit' }).click();
    await page.getByText('Status: submitted').waitFor();
    console.log('OK B06 UI: rejected claim resubmitted by owner');
    await shot('09-bob-resubmitted');

    if (jsErrors.length) console.log('JS CONSOLE ERRORS:', JSON.stringify(jsErrors, null, 2));
    else console.log('OK no JS console errors');
    console.log('ALL UI CHECKS PASSED');
  } catch (e) {
    console.error('FAILED:', e.message);
    const notes = await page.locator('vaadin-notification-card').allTextContents().catch(() => []);
    console.log('open notifications:', JSON.stringify(notes));
    if (jsErrors.length) console.log('JS CONSOLE ERRORS:', JSON.stringify(jsErrors, null, 2));
    await shot('failure');
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
