import { describe, it, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';
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

function sha256Prefix16(input) {
  return crypto.createHash('sha256').update(input, 'utf8').digest('hex').substring(0, 16);
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
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await seedBase();
});

describe('Gateway Payloads vs RTDB Rules Verification (Prompt 03 / F2a)', () => {

  describe('1. Device Registration (homes/$homeId/devices/$deviceId)', () => {
    it('ALLOW: Owner uA registers new device without deviceKey matching CloudMapper', async () => {
      const db = dbFor('uA');
      await assertSucceeds(
        db.ref('homes/home_A001/devices/dev_NEW0001').set({
          name: 'ESP32 Smart Home',
          model: 'esp32-smarthome',
          status: 'active',
          createdAt: SERVER_TS,
          createdBy: 'uA'
        })
      );
    });

    it('DENY: Device name containing deviceKey token is rejected', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/devices/dev_NEW0002').set({
          name: 'ESP32 deviceKey 123',
          model: 'esp32-smarthome',
          status: 'active',
          createdAt: SERVER_TS,
          createdBy: 'uA'
        })
      );
    });

    it('DENY: Payload contains extra field not in whitelist ($other: false)', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/devices/dev_NEW0003').set({
          name: 'ESP32 Smart Home',
          model: 'esp32-smarthome',
          status: 'active',
          createdAt: SERVER_TS,
          createdBy: 'uA',
          extraField: 'not_allowed'
        })
      );
    });

    it('DENY: Member uB cannot register a new device (Owner-only)', async () => {
      const db = dbFor('uB');
      await assertFails(
        db.ref('homes/home_A001/devices/dev_NEW0004').set({
          name: 'ESP32 Smart Home',
          model: 'esp32-smarthome',
          status: 'active',
          createdAt: SERVER_TS,
          createdBy: 'uB'
        })
      );
    });
  });

  describe('2. Gateway State (homes/$homeId/deviceData/$deviceId/state/gateway)', () => {
    it('ALLOW: Member uA writes whitelisted 8-value state on @ACTIVE device', async () => {
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
            alarm: false,
            motion: true,
            monitoring: true,
            mode: 'AWAY',
            temp: 28,
            hum: 65
          }
        })
      );
    });

    it('DENY: Writing state with forbidden firmware fields (raining, clothesRetracted)', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: {
            light: true,
            door: false,
            alarm: false,
            motion: true,
            monitoring: true,
            mode: 'HOME',
            raining: true,
            clothesRetracted: true
          }
        })
      );
    });

    it('DENY: Writing state on a revoked device (dev_D3001) fails @ACTIVE check', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D3001/state/gateway').set({
          online: true,
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: {
            light: false,
            door: false,
            alarm: false,
            motion: false,
            monitoring: false,
            mode: 'HOME'
          }
        })
      );
    });

    it('DENY: reportedBy mismatch with authenticated uid', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
          online: true,
          reportedBy: 'uB',
          receivedAt: SERVER_TS,
          values: {
            light: true,
            door: false,
            alarm: false,
            motion: false,
            monitoring: false,
            mode: 'HOME'
          }
        })
      );
    });
  });

  describe('3. Config Management (homes/$homeId/deviceData/$deviceId/config)', () => {
    it('ALLOW: Owner uA increments config rev from 1 to 2 with valid limits', async () => {
      const db = dbFor('uA');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 2,
          updatedBy: 'uA',
          updatedAt: SERVER_TS,
          tempLimit: 40,
          humLimit: 75,
          coldLimit: 15,
          autoAlarm: false
        })
      );
    });

    it('DENY: Writing with same rev=1 again is rejected (must be data.child("rev").val() + 1)', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 1,
          updatedBy: 'uA',
          updatedAt: SERVER_TS,
          tempLimit: 40,
          humLimit: 75,
          coldLimit: 15,
          autoAlarm: false
        })
      );
    });

    it('DENY: Member uB cannot update config (Owner-only)', async () => {
      const db = dbFor('uB');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/config').set({
          rev: 2,
          updatedBy: 'uB',
          updatedAt: SERVER_TS,
          tempLimit: 35,
          humLimit: 80,
          coldLimit: 18,
          autoAlarm: true
        })
      );
    });
  });

  describe('4. Telemetry Records (homes/$homeId/deviceData/$deviceId/telemetry/$recordId)', () => {
    it('ALLOW: Member uB creates new UTC telemetry record with _g suffix', async () => {
      const db = dbFor('uB');
      await assertSucceeds(
        db.ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611211345_g').set({
          source: 'gateway',
          reportedBy: 'uB',
          receivedAt: SERVER_TS,
          deviceTs: Date.now(),
          values: {
            temp: 29,
            hum: 62
          }
        })
      );
    });

    it('DENY: Overwriting existing telemetry record is rejected (create-only)', async () => {
      const db = dbFor('uB');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201430_g').set({
          source: 'gateway',
          reportedBy: 'uB',
          receivedAt: SERVER_TS,
          values: {
            temp: 30,
            hum: 70
          }
        })
      );
    });

    it('DENY: Telemetry write on revoked device is rejected', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D3001/telemetry/202611211346_g').set({
          source: 'gateway',
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          values: {
            temp: 25,
            hum: 60
          }
        })
      );
    });
  });

  describe('5. Event Records (homes/$homeId/deviceData/$deviceId/events/$eventId)', () => {
    const validStream = sha256Prefix16('esp32-stream-raw-1234'); // 16-hex characters

    it('ALLOW: Member uB publishes new event with SHA-256 stream matching eventId formula', async () => {
      const db = dbFor('uB');
      const eventId = `g_${validStream}_2`;
      await assertSucceeds(
        db.ref(`homes/home_A001/deviceData/dev_D1001/events/${eventId}`).set({
          type: 'rain_started',
          severity: 'warn',
          stream: validStream,
          seq: 2,
          source: 'gateway',
          reportedBy: 'uB',
          receivedAt: SERVER_TS,
          deviceTs: Date.now(),
          detail: 'boot=2;incident=0'
        })
      );
    });

    it('DENY: Raw ESP32 streamId with hyphen (e.g. "a1b2c3d4-9876") violates regex ^[A-Za-z0-9]{1,16}$', async () => {
      const db = dbFor('uB');
      const rawStreamWithHyphen = 'a1b2c3d4-9876';
      const eventId = `g_${rawStreamWithHyphen}_3`;
      await assertFails(
        db.ref(`homes/home_A001/deviceData/dev_D1001/events/${eventId}`).set({
          type: 'restarted',
          severity: 'info',
          stream: rawStreamWithHyphen,
          seq: 3,
          source: 'gateway',
          reportedBy: 'uB',
          receivedAt: SERVER_TS,
          detail: 'boot=3;incident=0'
        })
      );
    });

    it('DENY: Stream longer than 16 characters is rejected by regex', async () => {
      const db = dbFor('uB');
      const longStream = '0123456789abcdef0'; // 17 chars
      const eventId = `g_${longStream}_4`;
      await assertFails(
        db.ref(`homes/home_A001/deviceData/dev_D1001/events/${eventId}`).set({
          type: 'restarted',
          severity: 'info',
          stream: longStream,
          seq: 4,
          source: 'gateway',
          reportedBy: 'uB',
          receivedAt: SERVER_TS,
          detail: 'boot=4;incident=0'
        })
      );
    });

    it('DENY: Overwriting existing event is rejected (create-only)', async () => {
      const db = dbFor('uA');
      await assertFails(
        db.ref('homes/home_A001/deviceData/dev_D1001/events/g_0123456789abcdef_1').set({
          type: 'motion_alarm',
          severity: 'critical',
          stream: '0123456789abcdef',
          seq: 1,
          source: 'gateway',
          reportedBy: 'uA',
          receivedAt: SERVER_TS,
          detail: 'overwrite_attempt'
        })
      );
    });

    it('DENY: Non-member uC cannot publish events to home_A001', async () => {
      const db = dbFor('uC');
      const eventId = `g_${validStream}_5`;
      await assertFails(
        db.ref(`homes/home_A001/deviceData/dev_D1001/events/${eventId}`).set({
          type: 'restarted',
          severity: 'info',
          stream: validStream,
          seq: 5,
          source: 'gateway',
          reportedBy: 'uC',
          receivedAt: SERVER_TS,
          detail: 'boot=5;incident=0'
        })
      );
    });
  });
});
