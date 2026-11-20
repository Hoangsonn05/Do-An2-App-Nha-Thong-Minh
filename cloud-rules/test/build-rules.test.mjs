import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildRulesJson, expandExpression } from '../build-rules.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rulesDir = path.resolve(__dirname, '..');

test('expandExpression expands composite tokens before base tokens and preserves $ variables', () => {
  const expandedMember = expandExpression('@AUTH && @MEMBER');
  assert.match(expandedMember, /\$homeId/);
  assert.ok(!expandedMember.includes('@AUTH'));
  assert.ok(!expandedMember.includes('@MEMBER'));
  assert.ok(!expandedMember.includes('@OWNER'));
  assert.ok(
    expandedMember.includes("root.child('homes').child($homeId).child('members').child(auth.uid).exists()"),
    'Expected $homeId to survive replacement untouched'
  );

  const expandedDevice = expandExpression('@AUTH && @DEVICE');
  assert.ok(
    expandedDevice.includes("child($homeId).child('devices').child($deviceId).child('deviceAuthUid').val() === auth.uid"),
    'Expected $homeId and $deviceId to survive replacement untouched'
  );
  assert.ok(
    expandedDevice.includes("child($homeId).child('devices').child($deviceId).child('status').val() === 'active'"),
    'Expected @ACTIVE to expand inside @DEVICE'
  );
});

test('buildRulesJson rejects JSONC comments', () => {
  const jsonc = '{\n  // comment\n  "rules": { ".read": false }\n}';
  assert.throws(() => buildRulesJson(jsonc), SyntaxError);
});

test('buildRulesJson rejects unknown @TOKEN', () => {
  const bad = JSON.stringify({ rules: { '.read': '@UNKNOWN' } });
  assert.throws(() => buildRulesJson(bad), /Unexpanded rule token/);
});

test('database.rules.src.json parses as pure JSON and builds valid database.rules.json with zero unexpanded tokens', () => {
  const srcPath = path.join(rulesDir, 'database.rules.src.json');
  const srcText = fs.readFileSync(srcPath, 'utf8');
  const outText = buildRulesJson(srcText);
  const parsed = JSON.parse(outText);

  assert.equal(parsed.rules['.read'], false);
  assert.equal(parsed.rules['.write'], false);
  assert.equal(outText.match(/@[A-Z]+/g), null);

  // Verify F6 (@ACTIVE on command status cancel) and F9 (revoked terminal policy)
  const cmdStatusWrite = parsed.rules.homes.$homeId.deviceData.$deviceId.commands.$cmdId.status['.write'];
  assert.ok(
    cmdStatusWrite.includes("child('status').val() === 'active'"),
    'F6: commands/$cmdId/status/.write must require @ACTIVE'
  );

  const deviceStatusValidate = parsed.rules.homes.$homeId.devices.$deviceId.status['.validate'];
  assert.ok(
    deviceStatusValidate.includes("data.val() === 'active' && newData.val() === 'revoked'"),
    'F9: devices/$deviceId/status/.validate must enforce one-way active -> revoked transition'
  );
});
