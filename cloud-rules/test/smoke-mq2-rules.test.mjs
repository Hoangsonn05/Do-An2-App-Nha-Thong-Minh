import { describe, it, before, after, beforeEach } from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} from '@firebase/rules-unit-testing';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rulesPath = path.resolve(__dirname, '..', 'database.rules.json');

const PROJECT_ID = 'demo-smarthomeai';
const DB_HOST = '127.0.0.1';
const DB_PORT = 9000;
const SERVER_TS = { '.sv': 'timestamp' };
const PAST_TS = Date.now() - 86_400_000;

let testEnv;

function dbFor(uid) {
  if (!uid) {
    return testEnv.unauthenticatedContext().database();
  }
  return testEnv.authenticatedContext(uid).database();
}

function buildBaseSeed() {
  return {
    users: {
      uA: { displayName: 'Owner A', createdAt: PAST_TS, updatedAt: PAST_TS },
      uB: { displayName: 'Member B', createdAt: PAST_TS, updatedAt: PAST_TS },
      uC: { displayName: 'Stranger C', createdAt: PAST_TS, updatedAt: PAST_TS }
    },
    userHomes: {
      uA: { home_A001: 'owner' },
      uB: { home_A001: 'member' }
    },
    homes: {
      home_A001: {
        meta: {
          name: 'Nha Chinh A',
          ownerUid: 'uA',
          createdAt: PAST_TS,
          schemaVersion: 1
        },
        members: {
          uA: { role: 'owner', addedAt: PAST_TS, addedBy: 'uA' },
          uB: { role: 'member', addedAt: PAST_TS, addedBy: 'uA' }
        },
        devices: {
          dev_D1001: {
            name: 'Living Room ESP32',
            model: 'esp32-smarthome',
            status: 'active',
            deviceAuthUid: 'dev1',
            createdAt: PAST_TS,
            createdBy: 'uA'
          },
          dev_D3001: {
            name: 'Revoked ESP32',
            model: 'esp32-smarthome',
            status: 'revoked',
            createdAt: PAST_TS,
            createdBy: 'uA'
          }
        },
        deviceData: {
          dev_D1001: {
            config: {
              rev: 1,
              updatedBy: 'uA',
              updatedAt: PAST_TS,
              tempLimit: 35,
              humLimit: 80,
              coldLimit: 18,
              autoAlarm: true
            },
            telemetry: {
              '202611201430_g': {
                source: 'gateway',
                reportedBy: 'uA',
                receivedAt: PAST_TS,
                deviceTs: PAST_TS,
                values: { temp: 28, hum: 65 }
              }
            },
            events: {
              g_0123456789abcdef_1: {
                type: 'motion_alarm',
                severity: 'critical',
                stream: '0123456789abcdef',
                seq: 1,
                source: 'gateway',
                reportedBy: 'uA',
                deviceTs: PAST_TS,
                receivedAt: PAST_TS,
                detail: 'boot=1;incident=1'
              }
            }
          }
        }
      }
    }
  };
}

const PROBE_PATH = '__smoke_mq2_sync_probe__';
const SLEEP = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function waitForExclusiveEmulatorAccess() {
  const probeId = `${process.pid}_${Date.now()}`;
  const requiredQuietMs = 1000;
  const pollMs = 100;
  const maxWaitMs = 20_000;
  const startMs = Date.now();
  let stableSinceMs = null;

  while (Date.now() - startMs < maxWaitMs) {
    let matches = false;
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const ref = ctx.database().ref(PROBE_PATH);
      const snap = await ref.once('value');
      if (snap.val() === probeId) {
        matches = true;
      } else {
        await ref.set(probeId);
      }
    });

    if (matches) {
      if (stableSinceMs === null) {
        stableSinceMs = Date.now();
      } else if (Date.now() - stableSinceMs >= requiredQuietMs) {
        await testEnv.withSecurityRulesDisabled(async (ctx) => {
          await ctx.database().ref(PROBE_PATH).remove();
        });
        return;
      }
    } else {
      stableSinceMs = Date.now();
    }
    await SLEEP(pollMs);
  }
}

async function seedBase() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref('/').set(buildBaseSeed());
  });
}

before(async () => {
  const rules = fs.readFileSync(rulesPath, 'utf8');
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    database: {
      host: DB_HOST,
      port: DB_PORT,
      rules
    }
  });
  await waitForExclusiveEmulatorAccess();
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await seedBase();
});

describe('MQ-2 Smoke & Ventilation RTDB Rules Verification (Prompt 02 / MQ2-D2)', () => {

  describe('1. State (gateway & device) smokePpm, smokeAlert, window, fan', () => {
    it('ALLOW: Member uA writes state/gateway with smokePpm=320, smokeAlert=true, window=true, fan=true', async () => {
      const db = dbFor('uA');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          deviceTs: Date.now(),
          values: {
            light: true,
            door: false,
            alarm: true,
            motion: false,
            monitoring: true,
            mode: 'HOME',
            temp: 28,
            hum: 65,
            smokePpm: 320,
            smokeAlert: true,
            window: true,
            fan: true
          }
        })
      );
    });

    it('ALLOW: Device dev1 writes state/device with smokePpm, smokeAlert, window, fan', async () => {
      const db = dbFor('dev1');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
          online: true,
          reportedBy: 'dev1',
          receivedAt: SERVER_TS,
          values: {
            smokePpm: 0,
            smokeAlert: false,
            window: false,
            fan: false
          }
        })
      );
    });

    it('DENY: Writing state/gateway with smokePpm=-1 or smokePpm=10001 or window="open"', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: { smokePpm: -1 }
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: { smokePpm: 10001 }
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: { window: 'open' }
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: { fan: 1 }
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: { smokeAlert: 'yes' }
        })
      );
    });

    it('DENY: Writing state/device with smokePpm=-1 or smokePpm=10001 or window="open"', async () => {
      const db = dbFor('dev1');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
          online: true,
          reportedBy: 'dev1',
          receivedAt: SERVER_TS,
          values: { smokePpm: -1 }
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
          online: true,
          reportedBy: 'dev1',
          receivedAt: SERVER_TS,
          values: { smokePpm: 10001 }
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
          online: true,
          reportedBy: 'dev1',
          receivedAt: SERVER_TS,
          values: { window: 'open' }
        })
      );
    });
  });

  describe('2. Config smokeLimit (50..5000) & smokeAuto (bool)', () => {
    it('ALLOW: Owner uA writes config with smokeLimit=300, smokeAuto=true', async () => {
      const db = dbFor('uA');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 2,
          updatedBy: 'uA',
          updatedAt: SERVER_TS,
          tempLimit: 35,
          humLimit: 80,
          coldLimit: 18,
          autoAlarm: true,
          smokeLimit: 300,
          smokeAuto: true
        })
      );
    });

    it('ALLOW: Owner uA writes v1 config payload without smokeLimit or smokeAuto (backward compatible)', async () => {
      const db = dbFor('uA');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 2,
          updatedBy: 'uA',
          updatedAt: SERVER_TS,
          tempLimit: 38,
          humLimit: 75,
          coldLimit: 16,
          autoAlarm: false
        })
      );
    });

    it('DENY: Config with smokeLimit=10, smokeLimit=6000, or non-boolean smokeAuto', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 2,
          updatedBy: 'uA',
          updatedAt: SERVER_TS,
          tempLimit: 35,
          humLimit: 80,
          coldLimit: 18,
          autoAlarm: true,
          smokeLimit: 10,
          smokeAuto: true
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 2,
          updatedBy: 'uA',
          updatedAt: SERVER_TS,
          tempLimit: 35,
          humLimit: 80,
          coldLimit: 18,
          autoAlarm: true,
          smokeLimit: 6000,
          smokeAuto: true
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 2,
          updatedBy: 'uA',
          updatedAt: SERVER_TS,
          tempLimit: 35,
          humLimit: 80,
          coldLimit: 18,
          autoAlarm: true,
          smokeLimit: 300,
          smokeAuto: 'true'
        })
      );
    });
  });

  describe('3. Telemetry smokePpm (0..10000)', () => {
    it('ALLOW: Member uB creates telemetry with temp, hum and smokePpm=280', async () => {
      const db = dbFor('uB');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611261500_g').set({
          source: 'gateway',
          reportedBy: 'uB',
          receivedAt: SERVER_TS,
          deviceTs: Date.now(),
          values: {
            temp: 29,
            hum: 64,
            smokePpm: 280
          }
        })
      );
    });

    it('ALLOW: Member uA creates telemetry with smokePpm=280 only (no temp/hum)', async () => {
      const db = dbFor('uA');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611261501_g').set({
          source: 'gateway',
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          deviceTs: Date.now(),
          values: {
            smokePpm: 280
          }
        })
      );
    });

    it('DENY: Telemetry with smokePpm=-5 or smokePpm=10001', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611261502_g').set({
          source: 'gateway',
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: { smokePpm: -5 }
        })
      );
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611261503_g').set({
          source: 'gateway',
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: { smokePpm: 10001 }
        })
      );
    });
  });

  describe('4. Commands window.set & fan.set', () => {
    it('ALLOW: Member uB creates window.set and fan.set commands', async () => {
      const db = dbFor('uB');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_win_0001').set({
          type: 'window.set',
          params: { on: true },
          createdBy: 'uB',
          status: 'pending',
          createdAt: SERVER_TS,
          ttlMs: 30000
        })
      );
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_fan_0001').set({
          type: 'fan.set',
          params: { on: false },
          createdBy: 'uB',
          status: 'pending',
          createdAt: SERVER_TS,
          ttlMs: 30000
        })
      );
    });

    it('DENY: Unknown command type heater.set is rejected', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_bad_0001').set({
          type: 'heater.set',
          params: { on: true },
          createdBy: 'uA',
          status: 'pending',
          createdAt: SERVER_TS,
          ttlMs: 30000
        })
      );
    });
  });
});
