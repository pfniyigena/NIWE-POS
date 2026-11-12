// End-to-end check of the Online Store against a RUNNING backend: a customer places orders on the public shop and staff take them
// through accept, pack, delivery or pick-up and complete (invoice and cash). Use a development database: it creates real orders,
// sale orders, invoices and payments, and uses up a little stock.
//
//   node tools/online-store-smoke.mjs http://localhost:9090 admin <password>
//   (or set BASE, ADMIN_USER, ADMIN_PASSWORD; set SMOKE_EMAIL=you@example.com to also receive the customer's e-mails)
//
// Needs: Node 18 or newer; the Online Store module switched on and licensed; the shop open (Online Store > Shop settings); at least one
// product on the shop with stock; for the delivery part, a delivery area and a delivery-fee service product in the shop settings.
// Check the e-mail log or inbox yourself afterwards: this script cannot see mail.

const base = (process.argv[2] || process.env.BASE || 'http://localhost:8080').replace(/\/+$/, '');
const user = process.argv[3] || process.env.ADMIN_USER || 'admin';
const password = process.argv[4] || process.env.ADMIN_PASSWORD || '';
const pub = `${base}/api/public/store`;
const staff = `${base}/api/online-store`;

let passed = 0;
let failed = 0;
let token = null;
const stamp = String(Date.now()).slice(-6);

function ok(cond, what, extra = '') {
  if (cond) { passed++; console.log(`PASS  ${what}${extra ? '  ' + extra : ''}`); }
  else { failed++; console.log(`FAIL  ${what}${extra ? '  ' + extra : ''}`); }
  return cond;
}

async function call(method, url, body, auth = false) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
  return { status: res.status, json, text, headers: res.headers };
}

const place = (n, extra = {}) => call('POST', `${pub}/orders`, {
  name: `Smoke Test ${stamp}-${n}`, phone: `07880${stamp}${n}`.slice(0, 12), email: process.env.SMOKE_EMAIL || null, address: null, method: 'PICKUP', zoneId: null,
  notes: 'online-store-smoke', language: n % 2 ? 'fr' : 'en', website: '', lines: [], ...extra,
});

async function findOrder(code) {
  const r = await call('GET', `${staff}/orders?q=${encodeURIComponent(code)}&status=`, undefined, true);
  return Array.isArray(r.json) ? r.json.find((o) => o.code === code) : null;
}

async function step(order, what, path, body) {
  const r = await call('POST', `${staff}/orders/${order.id}/${path}`, body ?? {}, true);
  ok(r.status === 200, `staff: ${what}`, r.status === 200 ? `-> ${r.json?.status}` : `HTTP ${r.status} ${r.json?.message ?? r.text.slice(0, 160)}`);
  return r.status === 200 ? r.json : null;
}

async function main() {
  console.log(`Shop at ${base}\n`);

  // ---- the shop and its catalogue
  const shop = await call('GET', `${pub}/shop`);
  if (!ok(shop.status === 200, 'public: the shop answers', shop.status === 200 ? `"${shop.json.name}"` : `HTTP ${shop.status} ${shop.json?.message ?? ''} (is the module on and the shop open?)`)) return;
  const s = shop.json;
  ok(s.open, 'public: the shop is taking orders');
  ok(Array.isArray(s.categories), 'public: the shop lists its categories', `(${s.categories?.length})`);

  const page1 = await call('GET', `${pub}/products?page=0&size=16`);
  ok(page1.status === 200 && Array.isArray(page1.json?.items), 'public: first page of products', `(${page1.json?.items?.length} of ${page1.json?.total}, ${page1.json?.totalPages} pages)`);
  const items = page1.json?.items ?? [];
  ok(items.length <= 16, 'public: a page holds at most 16 products');
  if (page1.json?.totalPages > 1) {
    const page2 = await call('GET', `${pub}/products?page=1&size=16`);
    const ids1 = new Set(items.map((p) => p.id));
    ok(page2.status === 200 && page2.json.items.length > 0 && page2.json.items.every((p) => !ids1.has(p.id)), 'public: the second page has other products');
  }
  const product = items.find((p) => p.stock !== 'SOLD_OUT');
  if (!ok(!!product, 'public: a product with stock exists on the first page')) return;
  const word = product.name.split(/\s+/).find((w) => w.length >= 3) ?? product.name;
  const search = await call('GET', `${pub}/products?q=${encodeURIComponent(word)}`);
  ok(search.status === 200 && search.json.items.some((p) => p.id === product.id), `public: searching "${word}" finds the product`);
  if (product.category) {
    const byCat = await call('GET', `${pub}/products?category=${encodeURIComponent(product.category)}`);
    ok(byCat.status === 200 && byCat.json.items.every((p) => p.category === product.category), `public: the category "${product.category}" filters the list`);
  }
  const withPhoto = items.find((p) => p.imageUrl);
  if (withPhoto) {
    const full = await fetch(`${base}${withPhoto.imageUrl}`);
    const small = await fetch(`${base}${withPhoto.imageUrl}?w=420`);
    const fullLen = (await full.arrayBuffer()).byteLength;
    const smallLen = (await small.arrayBuffer()).byteLength;
    ok(full.status === 200 && small.status === 200 && (small.headers.get('content-type') ?? '').startsWith('image/'), 'public: the product photo loads, full size and shrunk');
    console.log(`      photo: ${Math.round(fullLen / 1024)} KB full, ${Math.round(smallLen / 1024)} KB at 420 px wide`);
  } else {
    console.log('SKIP  no product on the first page has a photo');
  }

  // ---- what a customer must not be able to do
  const bad = await place(0, { name: '', lines: [{ productId: product.id, quantity: 1 }] });
  ok(bad.status >= 400 && bad.status < 500, 'public: an order without a name is refused', `HTTP ${bad.status} "${bad.json?.message ?? ''}"`);
  const empty = await place(0, { lines: [] });
  ok(empty.status >= 400 && empty.status < 500, 'public: an order with no products is refused', `HTTP ${empty.status}`);
  const bot = await place(0, { website: 'http://spam.example', lines: [{ productId: product.id, quantity: 1 }] });
  ok(bot.status >= 400 && bot.status < 500, 'public: the hidden bot field is refused', `HTTP ${bot.status}`);

  // ---- staff sign in
  const login = await call('POST', `${base}/api/auth/login`, { username: user, password });
  if (!ok(login.status === 200 && !!login.json?.token, 'staff: sign in', login.status === 200 ? `as ${login.json.username}` : `HTTP ${login.status}`)) return;
  token = login.json.token;
  const lines = [{ productId: product.id, quantity: 1 }];

  // ---- 1. pick-up order, all the way to the invoice
  const a = await place(1, { method: 'PICKUP', lines });
  if (ok(a.status === 200 && a.json?.status === 'PLACED', 'public: pick-up order placed', a.status === 200 ? `code ${a.json.code}` : `HTTP ${a.status} ${a.json?.message ?? ''}`)) {
    const code = a.json.code;
    const tr = await call('GET', `${pub}/orders/${code}`);
    ok(tr.status === 200 && tr.json.code === code, 'public: the customer can follow the order by its code');
    const o = await findOrder(code);
    if (ok(!!o, 'staff: the order is in the Online orders list')) {
      const acc = await step(o, 'accept (creates the sale order)', 'accept', { overrideShortage: false });
      if (acc) {
        ok(!!acc.salesOrderNumber, 'staff: a sale order was created', acc.salesOrderNumber ?? '');
        await step(o, 'mark packed', 'pack', {});
        await step(o, 'ready for pick-up', 'ready-for-pickup', {});
        const done = await step(o, 'complete (invoice and cash)', 'complete', { collectPayment: true, method: 'CASH' });
        if (done) {
          ok(!!done.invoiceId, 'staff: an invoice was issued', done.invoiceNumber ?? '');
          const inv = await call('GET', `${base}/api/invoices/${done.invoiceId}`, undefined, true);
          ok(inv.status === 200, 'staff: the invoice can be opened', inv.status === 200 ? `status ${inv.json.status ?? '?'}, total ${inv.json.totalAmount ?? '?'}` : `HTTP ${inv.status}`);
        }
        const after = await call('GET', `${pub}/orders/${code}`);
        const steps = (after.json?.history ?? []).map((h) => h.status);
        ok(['PLACED', 'ACCEPTED', 'PACKED', 'READY_FOR_PICKUP', 'COMPLETED'].every((x) => steps.includes(x)), 'public: the order page shows every step', steps.join(' > '));
      }
    }
  }

  // ---- 2. delivery order
  if (s.delivery && s.zones?.length) {
    const zone = s.zones[0];
    const d = await place(2, { method: 'DELIVERY', zoneId: zone.id, address: 'Smoke test street 1', lines });
    if (ok(d.status === 200 && d.json?.status === 'PLACED', 'public: delivery order placed', d.status === 200 ? `code ${d.json.code}, fee ${d.json.deliveryFee}` : `HTTP ${d.status} ${d.json?.message ?? ''}`)) {
      const o = await findOrder(d.json.code);
      if (o) {
        const acc = await step(o, 'accept the delivery order (needs the delivery-fee service product in the shop settings)', 'accept', { overrideShortage: false });
        if (acc) {
          await step(o, 'out for delivery', 'out-for-delivery', {});
          await step(o, 'complete the delivery order', 'complete', { collectPayment: true, method: 'CASH' });
        }
      }
    }
  } else {
    console.log('SKIP  delivery is off or there is no delivery area: delivery order not tried');
  }

  // ---- 3. the customer cancels while the order is new
  const c = await place(3, { lines });
  if (ok(c.status === 200, 'public: a third order placed (to cancel)')) {
    const cancel = await call('POST', `${pub}/orders/${c.json.code}/cancel`, {});
    ok(cancel.status === 200 && cancel.json.status === 'CANCELLED', 'public: the customer cancelled it while new');
    const again = await call('POST', `${pub}/orders/${c.json.code}/cancel`, {});
    ok(again.status >= 400, 'public: cancelling twice is refused', `HTTP ${again.status}`);
  }

  // ---- 4. staff reject
  const r = await place(4, { lines });
  if (ok(r.status === 200, 'public: a fourth order placed (to reject)')) {
    const o = await findOrder(r.json.code);
    if (o) {
      const noReason = await call('POST', `${staff}/orders/${o.id}/reject`, { reason: '' }, true);
      ok(noReason.status >= 400, 'staff: rejecting needs a reason', `HTTP ${noReason.status}`);
      await step(o, 'reject with a reason', 'reject', { reason: 'Smoke test: out of stock' });
      const after = await call('GET', `${pub}/orders/${r.json.code}`);
      ok(after.json?.status === 'REJECTED' && !!after.json?.rejectReason, 'public: the customer sees the rejection and its reason', after.json?.rejectReason ?? '');
    }
  }

  // ---- the staff screen counters
  const pc = await call('GET', `${staff}/orders/pending-count`, undefined, true);
  ok(pc.status === 200 && typeof pc.json?.pending === 'number', 'staff: the waiting-orders counter answers', pc.status === 200 ? JSON.stringify(pc.json) : `HTTP ${pc.status}`);
}

try {
  await main();
} catch (e) {
  failed++;
  console.log(`FAIL  the script stopped: ${e.message}`);
}
console.log(`\n${passed} passed, ${failed} failed.`);
console.log('Now look at the mail log or the inbox: the shop should have one "New online order" mail per order, and a customer with an e-mail address gets one per step.');
process.exit(failed ? 1 : 0);
