// v1.23.0: turns the English text in Angular inline templates into translatable keys.
//   node ../tools/i18n/transform.mjs [--dry] <file.ts> ...      (run from the frontend folder)
// The English text itself is the key:  Save  ->  {{ 'Save' | translate }},  placeholder="Search" -> [placeholder]="'Search' | translate".
// Text with {{ x }} in it becomes a key with {p0} holes:  {{ 'Total: {p0} items' | translate: { p0: (x) } }}.
// Re-running is safe (already converted text is skipped). The keys are merged into ../tools/i18n/keys.json, and fr.json is where they are translated.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
const dry = args.includes('--dry');
const files = args.filter((a) => !a.startsWith('--'));
const req = createRequire(path.join(process.cwd(), 'package.json'));
const { parseTemplate } = await import(pathToFileURL(req.resolve('@angular/compiler')).href);
const keysFile = path.resolve(process.cwd(), '../tools/i18n/keys.json');
const keys = fs.existsSync(keysFile) ? JSON.parse(fs.readFileSync(keysFile, 'utf8')) : {};
const report = { files: 0, changed: 0, skipped: [], keys: 0, leftovers: [] };

const ATTRS = { placeholder: '[placeholder]', title: '[title]', alt: '[alt]', 'aria-label': '[attr.aria-label]', label: '[label]' };
const SKIP_TAGS = new Set(['pre', 'code', 'style', 'script', 'textarea', 'svg']);
const hasLetter = (s) => /\p{L}/u.test(s);
const norm = (s) => s.replace(/\s+/g, ' ').trim();
const jsEscape = (s) => s.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${');
const ngStr = (s) => s.replace(/\\/g, '\\\\').replace(/'/g, "\\'");

function walk(node, visit, seen = new Set(), ctx = {}) {
  if (!node || typeof node !== 'object' || seen.has(node)) return;
  seen.add(node);
  const name = (node.constructor?.name || '').replace(/\$\d+$/, '');
  const inSkip = ctx.skip || (name === 'Element' && SKIP_TAGS.has(String(node.name).toLowerCase()));
  visit(node, name, inSkip);
  for (const k of ['children', 'branches', 'cases', 'empty', 'placeholder', 'loading', 'error', 'block']) {
    const v = node[k];
    if (Array.isArray(v)) v.forEach((c) => walk(c, visit, seen, { skip: inSkip }));
    else if (v && typeof v === 'object') walk(v, visit, seen, { skip: inSkip });
  }
}

function transformTemplate(tpl, file) {
  const parsed = parseTemplate(tpl, 'x.html', { preserveWhitespaces: true });
  if (parsed.errors?.length) { report.skipped.push(`${file}: template errors ${parsed.errors[0].msg ?? parsed.errors[0]}`); return null; }
  const edits = new Map(); // start -> {start,end,text}
  const found = [];
  const add = (start, end, text) => { if (!edits.has(start)) edits.set(start, { start, end, text: jsEscape(text) }); };
  const raw = (span) => tpl.slice(span.start.offset, span.end.offset);

  walk({ children: parsed.nodes }, (node, name, skip) => {
    if (skip) return;
    if (name === 'Text') {
      const src = raw(node.sourceSpan);
      if (/[&]/.test(src) || !hasLetter(src)) return;
      const lead = src.length - src.trimStart().length;
      const core = src.trim();
      if (!core) return;
      const key = norm(core);
      add(node.sourceSpan.start.offset + lead, node.sourceSpan.start.offset + lead + core.length, `{{ '${ngStr(key)}' | translate }}`);
      found.push(key);
    } else if (name === 'BoundText') {
      const src = raw(node.sourceSpan);
      const lead = src.length - src.trimStart().length;
      const core = src.trim();
      const parts = core.split(/(\{\{[\s\S]*?\}\})/);
      const lits = parts.filter((p) => !p.startsWith('{{')).join('');
      const exprs = parts.filter((p) => p.startsWith('{{')).map((p) => p.slice(2, -2).trim());
      if (/[&]/.test(lits)) return;
      const start = node.sourceSpan.start.offset + lead;
      if (hasLetter(lits)) {
        let i = 0;
        const key = norm(parts.map((p) => (p.startsWith('{{') ? `{p${i++}}` : p)).join(''));
        if (exprs.some((e) => e.includes('translate'))) return;
        const params = exprs.map((e, n) => `p${n}: (${e})`).join(', ');
        add(start, start + core.length, `{{ '${ngStr(key)}' | translate${exprs.length ? `: { ${params} }` : ''} }}`);
        found.push(key);
      } else if (exprs.length === 1 && parts.filter((p) => p.trim()).length === 1) {
        const e = exprs[0];
        if (e.includes('translate')) return;
        const m = e.match(/^([\s\S]+?)\s\?\s'([^'\\]*)'\s:\s'([^'\\]*)'$/);
        if (m && (hasLetter(m[2]) || hasLetter(m[3]))) {
          const t = (s) => (hasLetter(s) ? `('${ngStr(s)}' | translate)` : `'${s}'`);
          add(start, start + core.length, `{{ ${m[1]} ? ${t(m[2])} : ${t(m[3])} }}`);
          if (hasLetter(m[2])) found.push(m[2]);
          if (hasLetter(m[3])) found.push(m[3]);
        } else if (/'[^']*\p{L}[^']*'/u.test(e)) {
          report.leftovers.push(`${file}: {{ ${e.slice(0, 90)} }}`);
        }
      }
    } else if (name === 'Element' || name === 'Template') {
      for (const a of node.attributes ?? []) {
        const bind = ATTRS[a.name];
        if (!bind || !a.valueSpan || !hasLetter(a.value) || /["{}&]/.test(a.value)) continue;
        const key = norm(a.value);
        add(a.sourceSpan.start.offset, a.sourceSpan.end.offset, `${bind}="'${ngStr(key)}' | translate"`);
        found.push(key);
      }
    }
  });
  if (!edits.size) return null;
  let out = tpl;
  [...edits.values()].sort((a, b) => b.start - a.start).forEach((e) => { out = out.slice(0, e.start) + e.text + out.slice(e.end); });
  return { out, found };
}


for (const file of files) {
  report.files++;
  let src = fs.readFileSync(file, 'utf8');
  const crlf = src.includes('\r\n');
  if (crlf) src = src.replace(/\r\n/g, '\n');
  // every inline template: template: `...`
  const re = /template:\s*`/g;
  const spots = [];
  let m;
  while ((m = re.exec(src))) {
    const start = m.index + m[0].length;
    const end = src.indexOf('`', start);
    spots.push({ at: m.index, start, end });
  }
  if (!spots.length) continue;
  let changed = false;
  const allFound = [];
  for (const sp of spots.reverse()) {
    const tpl = src.slice(sp.start, sp.end);
    if (/\\|\$\{/.test(tpl)) { report.skipped.push(`${file}: template has \\ or \${ (left as is)`); continue; }
    const r = transformTemplate(tpl, file);
    if (!r) continue;
    src = src.slice(0, sp.start) + r.out + src.slice(sp.end);
    allFound.push(...r.found);
    // import the pipe in this component
    const deco = src.lastIndexOf('@Component(', sp.at);
    const after = src.slice(deco, sp.start);
    const im = after.match(/imports:\s*\[/);
    if (im) {
      const pos = deco + im.index + im[0].length;
      if (!/TranslatePipe/.test(src.slice(pos, pos + 400).split(']')[0])) src = src.slice(0, pos) + 'TranslatePipe, ' + src.slice(pos);
    } else {
      const sa = after.match(/standalone:\s*true,?/);
      if (sa) {
        const pos = deco + sa.index + sa[0].length;
        src = src.slice(0, pos) + (sa[0].endsWith(',') ? '' : ',') + '\n  imports: [TranslatePipe],' + src.slice(pos);
      } else { report.skipped.push(`${file}: component is not standalone / no imports`); }
    }
    changed = true;
  }
  if (!changed) continue;
  if (!/from '@ngx-translate\/core'/.test(src)) {
    const first = src.match(/^import [^\n]*;$/m);
    src = first ? src.replace(first[0], first[0] + "\nimport { TranslatePipe } from '@ngx-translate/core';") : "import { TranslatePipe } from '@ngx-translate/core';\n" + src;
  }
  for (const k of allFound) { (keys[k] ??= []); if (!keys[k].includes(file) && keys[k].length < 3) keys[k].push(file); }
  report.changed++;
  report.keys += allFound.length;
  if (!dry) fs.writeFileSync(file, crlf ? src.replace(/\n/g, '\r\n') : src, 'utf8');
}
if (!dry) fs.writeFileSync(keysFile, JSON.stringify(keys, null, 1), 'utf8');
console.log(JSON.stringify({ dry, files: report.files, changed: report.changed, keysFound: report.keys, totalKeys: Object.keys(keys).length, skipped: report.skipped, leftovers: report.leftovers.slice(0, 40), leftoversCount: report.leftovers.length }, null, 1));
