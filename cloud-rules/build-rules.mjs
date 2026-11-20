import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const TOKENS = [
  ['@MEMBER', "(root.child('homes').child($homeId).child('members').child(auth.uid).exists() || @OWNER)"],
  ['@DEVICE', "(root.child('homes').child($homeId).child('devices').child($deviceId).child('deviceAuthUid').val() === auth.uid && @ACTIVE)"],
  ['@OWNER', "(root.child('homes').child($homeId).child('meta').child('ownerUid').val() === auth.uid)"],
  ['@ACTIVE', "(root.child('homes').child($homeId).child('devices').child($deviceId).child('status').val() === 'active')"],
  ['@AUTH', '(auth != null)']
];

const UNEXPANDED_TOKEN_RE = /@[A-Z]+/g;
const MAX_PASSES = 5;

export function expandExpression(expr) {
  let current = expr;
  for (let pass = 0; pass < MAX_PASSES; pass++) {
    let next = current;
    for (const [token, replacement] of TOKENS) {
      if (next.includes(token)) {
        // Callback replacer avoids special '$' interpretation ($homeId, $deviceId, etc.).
        next = next.replaceAll(token, () => replacement);
      }
    }
    if (next === current) break;
    current = next;
  }

  const remaining = current.match(UNEXPANDED_TOKEN_RE);
  if (remaining) {
    throw new Error(`Unexpanded rule token(s) found: ${remaining.join(', ')} in "${current}"`);
  }
  return current;
}

export function expandRulesNode(node) {
  if (typeof node === 'string') {
    return expandExpression(node);
  }
  if (Array.isArray(node)) {
    return node.map((item) => expandRulesNode(item));
  }
  if (node !== null && typeof node === 'object') {
    const out = {};
    for (const [key, value] of Object.entries(node)) {
      out[key] = expandRulesNode(value);
    }
    return out;
  }
  return node;
}

export function buildRulesJson(srcText) {
  // Pure JSON parse: rejects JSONC comments or trailing commas.
  const parsed = JSON.parse(srcText);
  if (!parsed || typeof parsed !== 'object' || !parsed.rules) {
    throw new Error('Source rules JSON must contain a top-level "rules" object.');
  }
  const expanded = expandRulesNode(parsed);
  const output = JSON.stringify(expanded, null, 2) + '\n';

  const strayTokens = output.match(UNEXPANDED_TOKEN_RE);
  if (strayTokens) {
    throw new Error(`Unexpanded token(s) detected in output: ${strayTokens.join(', ')}`);
  }

  // Validate generated output is pure parseable JSON.
  JSON.parse(output);
  return output;
}

export function buildRulesFile(
  srcPath = path.join(__dirname, 'database.rules.src.json'),
  outPath = path.join(__dirname, 'database.rules.json')
) {
  const srcText = fs.readFileSync(srcPath, 'utf8');
  const output = buildRulesJson(srcText);
  fs.writeFileSync(outPath, output, 'utf8');
  return { srcPath, outPath, bytes: Buffer.byteLength(output, 'utf8') };
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  try {
    const res = buildRulesFile();
    console.log(`Built ${path.basename(res.outPath)} (${res.bytes} bytes) from ${path.basename(res.srcPath)}.`);
  } catch (err) {
    console.error('build-rules failed:', err.message);
    process.exit(1);
  }
}
