// Turns frontend/src/app/core/i18n/fr.json (the French texts, English text -> French) into fr.ts, which the app imports.
//   node ../tools/i18n/build-fr.mjs        (from the frontend folder)
// Add a phrase: run transform.mjs on the screen, then add its English text and French to fr.json, then run this.
import fs from 'node:fs';
const dir = 'src/app/core/i18n';
const fr = JSON.parse(fs.readFileSync(`${dir}/fr.json`, 'utf8'));
const lines = Object.keys(fr).sort().map((k) => `  ${JSON.stringify(k)}: ${JSON.stringify(fr[k])},`);
fs.writeFileSync(`${dir}/fr.ts`, `// GENERATED from fr.json by tools/i18n/build-fr.mjs - do not edit by hand.\nexport const FR: Record<string, string> = {\n${lines.join('\n')}\n};\n`, 'utf8');
console.log(Object.keys(fr).length, 'texts');
