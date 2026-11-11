// v1.25.0: translates messages written inside TypeScript (this.error = '...', confirm('...'), ...) by wrapping them in T('English', {p0: value}).
// Usage (from frontend/): node ../tools/i18n/transform-ts.mjs [--dry] [--discover] src/app/<dir>/file.ts ...   (or a directory)
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
const dry = args.includes('--dry');
const discover = args.includes('--discover');
const targets = args.filter((a) => !a.startsWith('--'));
const req = createRequire(path.join(process.cwd(), 'package.json'));
const ts = (await import(pathToFileURL(req.resolve('typescript')).href)).default;
const keysFile = path.resolve(process.cwd(), '../tools/i18n/keys.json');
const keys = fs.existsSync(keysFile) ? JSON.parse(fs.readFileSync(keysFile, 'utf8')) : {};

const LABELS = new Set((process.env.I18N_LABELS || '').split(',').filter(Boolean));
const outside = [];
const PROPS = new Set(['error', 'message', 'info', 'notice', 'msg', 'success', 'warning', 'err', 'errorMessage', 'infoMessage', 'okMessage', 'saveError', 'formError', 'flash', 'toast', 'hint']);
const CALLS = new Set(['confirm', 'alert', 'run', 'fail', 'showError', 'showMessage', 'setError', 'setMessage', 'flash', 'toast', 'notify']);
const hasLetter = (s) => /\p{L}/u.test(s);
const q = (s) => "'" + s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '') + "'";

const found = new Set();
const stats = { props: {}, calls: {} };

function conv(node, sf, nested = false) {
  if (ts.isParenthesizedExpression(node)) { const r = conv(node.expression, sf, nested); return r ? '(' + r + ')' : null; }
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    const t = node.text;
    if (!hasLetter(t) || /[{}]/.test(t)) return null;
    if (nested && t.length < 4 && !t.includes(' ')) return null;
    found.add(t);
    return `T(${q(t)})`;
  }
  if (ts.isTemplateExpression(node)) {
    const params = []; let key = node.head.text;
    node.templateSpans.forEach((sp) => {
      const e = conv(sp.expression, sf, true) ?? sp.expression.getText(sf);
      let i = params.indexOf(e); if (i < 0) { params.push(e); i = params.length - 1; }
      key += `{p${i}}` + sp.literal.text;
    });
    if (!hasLetter(key.replace(/\{p\d+\}/g, '')) || /[{}]/.test(key.replace(/\{p\d+\}/g, ''))) return null;
    found.add(key);
    return `T(${q(key)}, { ${params.map((e, i) => `p${i}: ${e}`).join(', ')} })`;
  }
  if (ts.isConditionalExpression(node)) {
    const a = conv(node.whenTrue, sf, nested), b = conv(node.whenFalse, sf, nested);
    if (!a && !b) return null;
    return `${node.condition.getText(sf)} ? ${a ?? node.whenTrue.getText(sf)} : ${b ?? node.whenFalse.getText(sf)}`;
  }
  if (ts.isBinaryExpression(node)) {
    const op = node.operatorToken.kind;
    if (op === ts.SyntaxKind.BarBarToken || op === ts.SyntaxKind.QuestionQuestionToken) {
      const r = conv(node.right, sf, nested);
      if (!r) return null;
      return `${node.left.getText(sf)} ${node.operatorToken.getText(sf)} ${r}`;
    }
    if (op === ts.SyntaxKind.PlusToken) {
      // a chain 'text ' + x + ' more' whose first operand is a string literal
      const parts = []; let n = node;
      while (ts.isBinaryExpression(n) && n.operatorToken.kind === ts.SyntaxKind.PlusToken) { parts.unshift(n.right); n = n.left; }
      parts.unshift(n);
      if (!(ts.isStringLiteral(parts[0]) || ts.isNoSubstitutionTemplateLiteral(parts[0]))) return null;
      const params = []; let key = '';
      for (const p of parts) {
        if (ts.isStringLiteral(p) || ts.isNoSubstitutionTemplateLiteral(p)) key += p.text;
        else { const e = conv(p, sf, true) ?? p.getText(sf); let i = params.indexOf(e); if (i < 0) { params.push(e); i = params.length - 1; } key += `{p${i}}`; }
      }
      const plain = key.replace(/\{p\d+\}/g, '');
      if (!hasLetter(plain) || /[{}]/.test(plain)) return null;
      found.add(key);
      return params.length ? `T(${q(key)}, { ${params.map((e, i) => `p${i}: ${e}`).join(', ')} })` : `T(${q(key)})`;
    }
  }
  return null;
}

function relImport(file) {
  const target = path.resolve(process.cwd(), 'src/app/core/i18n/t');
  let r = path.relative(path.dirname(path.resolve(file)), target).replace(/\\/g, '/');
  if (!r.startsWith('.')) r = './' + r;
  return r;
}

function processFile(file) {
  let src = fs.readFileSync(file, 'utf8');
  const crlf = src.includes('\r\n');
  if (crlf) src = src.replace(/\r\n/g, '\n');
  const sf = ts.createSourceFile(file, src, ts.ScriptTarget.Latest, true);
  const edits = [];
  function visit(node) {
    let done = false;
    if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.EqualsToken && ts.isPropertyAccessExpression(node.left)) {
      const name = node.left.name.text;
      if (PROPS.has(name)) {
        const r = conv(node.right, sf);
        if (discover) stats.props[name] = (stats.props[name] || 0) + (r ? 1 : 0);
        if (r) { edits.push([node.right.getStart(sf), node.right.getEnd(), r]); done = true; }
      }
    }
    if (!done && LABELS.size && ts.isPropertyAssignment(node) && ts.isIdentifier(node.name) && LABELS.has(node.name.text) && (ts.isStringLiteral(node.initializer) || ts.isNoSubstitutionTemplateLiteral(node.initializer))) {
      let inClass = false; for (let p = node.parent; p; p = p.parent) if (ts.isClassDeclaration(p)) { inClass = true; break; }
      const r = conv(node.initializer, sf);
      if (r) {
        if (inClass) { edits.push([node.initializer.getStart(sf), node.initializer.getEnd(), r]); done = true; }
        else if (/report-defs/.test(file)) { outside.push(`${file}: ${node.getText(sf).slice(0, 80)}`); found.delete(node.initializer.text); }
        else { edits.push([node.getStart(sf), node.getEnd(), `get ${node.name.text}() { return ${r}; }`]); done = true; }  // module-level list: translated when read, after the language is loaded
      }
    }
    if (!done && ts.isCallExpression(node)) {
      const c = node.expression;
      const name = ts.isIdentifier(c) ? c.text : ts.isPropertyAccessExpression(c) ? c.name.text : '';
      const onThis = ts.isIdentifier(c) || (ts.isPropertyAccessExpression(c) && c.expression.kind === ts.SyntaxKind.ThisKeyword);
      if (CALLS.has(name) && onThis) {
        node.arguments.forEach((a) => {
          if (ts.isStringLiteral(a) || ts.isNoSubstitutionTemplateLiteral(a) || ts.isTemplateExpression(a) || ts.isBinaryExpression(a) || ts.isConditionalExpression(a) || ts.isParenthesizedExpression(a)) {
            const r = conv(a, sf);
            if (discover) stats.calls[name] = (stats.calls[name] || 0) + (r ? 1 : 0);
            if (r) edits.push([a.getStart(sf), a.getEnd(), r]);
          }
        });
      }
    }
    if (!done) ts.forEachChild(node, visit);
  }
  visit(sf);
  if (!edits.length) return 0;
  edits.sort((a, b) => b[0] - a[0]);
  for (const [s, e, r] of edits) src = src.slice(0, s) + r + src.slice(e);
  if (!/from\s+['"][^'"]*core\/i18n\/t['"]/.test(src)) {
    const m = [...src.matchAll(/^import[\s\S]*?;\s*$/gm)];
    const at = m.length ? m[m.length - 1].index + m[m.length - 1][0].length : 0;
    src = src.slice(0, at) + `\nimport { T } from '${relImport(file)}';` + src.slice(at);
  }
  if (!dry && !discover) fs.writeFileSync(file, crlf ? src.replace(/\n/g, '\r\n') : src, 'utf8');
  if (dry) console.log(file, edits.length);
  return edits.length;
}

function* walk(p) {
  const st = fs.statSync(p);
  if (st.isDirectory()) { for (const f of fs.readdirSync(p)) yield* walk(path.join(p, f)); }
  else if (p.endsWith('.ts') && !p.endsWith('.spec.ts') && !p.includes(path.join('core', 'i18n'))) yield p;
}

let files = 0, changed = 0, total = 0;
for (const t of targets) for (const f of walk(t)) { files++; const n = processFile(f); if (n) { changed++; total += n; } }
if (!dry && !discover) {
  for (const k of found) { keys[k] ??= []; }
  fs.writeFileSync(keysFile, JSON.stringify(keys, null, 1), 'utf8');
}
if (outside.length) fs.writeFileSync(path.resolve(process.cwd(), '../tools/i18n/_outside.txt'), outside.join('\n'));
console.log(JSON.stringify({ outside: outside.length, files, changed, edits: total, phrases: found.size, ...(discover ? stats : {}) }, null, 1));
