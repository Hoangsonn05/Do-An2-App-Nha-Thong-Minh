import { describe, it, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
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
const PAST_TS = Date.now() - 86_400_000; // 24h in the past (F2: #13, #23, #30)

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
      uA: {
        displayName: 'Owner A',
        createdAt: PAST_TS,
        updatedAt: PAST_TS
      }
    },
    userHomes: {
      uA: { home_A001: 'owner' },
      uB: { home_A001: 'member' },
      uC: { home_C001: 'owner' }
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
          dev_D4002: {
            // F2 (#25): active device without deviceAuthUid yet
            name: 'Unbound ESP32',
            model: 'esp32-smarthome',
            status: 'active',
            createdAt: PAST_TS,
            createdBy: 'uA'
          },
          dev_D3001: {
            name: 'Revoked ESP32',
            model: 'esp32-smarthome',
            status: 'revoked',
            deviceAuthUid: 'dev3',
            createdAt: PAST_TS,
            createdBy: 'uA'
          }
        },
        deviceData: {
          dev_D1001: {
            state: {
              gateway: {
                online: true,
                reportedBy: 'uA',
                receivedAt: PAST_TS,
                deviceTs: PAST_TS,
                values: {
                  light: true,
                  door: false,
                  alarm: false,
                  temp: 28,
                  hum: 65,
                  motion: false,
                  monitoring: false,
                  mode: 'HOME'
                }
              },
              device: {
                online: true,
                reportedBy: 'dev1',
                receivedAt: PAST_TS,
                deviceTs: PAST_TS,
                values: {
                  light: true,
                  door: false,
                  alarm: false,
                  temp: 28,
                  hum: 65,
                  motion: false,
                  monitoring: false,
                  mode: 'HOME'
                }
              }
            },
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
                reportedBy: 'uB',
                receivedAt: PAST_TS,
                deviceTs: PAST_TS,
                values: { temp: 28, hum: 65 }
              },
              '202611201430_d': {
                source: 'device',
                reportedBy: 'dev1',
                receivedAt: PAST_TS + 1000,
                deviceTs: PAST_TS + 1000,
                values: { temp: 29, hum: 64 }
              }
            },
            commands: {
              cmd_00001: {
                type: 'light.set',
                params: { on: true },
                createdBy: 'uB',
                status: 'pending',
                createdAt: PAST_TS,
                ttlMs: 60000
              },
              cmd_00002: {
                type: 'door.set',
                params: { on: false },
                createdBy: 'uB',
                status: 'pending',
                createdAt: PAST_TS + 2000,
                ttlMs: 60000
              }
            },
            acks: {
              cmd_00002: {
                cmdId: 'cmd_00002',
                result: 'ok',
                reportedBy: 'dev1',
                receivedAt: PAST_TS + 2500
              }
            },
            events: {
              g_s7_142: {
                type: 'door.opened',
                severity: 'info',
                stream: 's7',
                seq: 142,
                source: 'gateway',
                reportedBy: 'uB',
                deviceTs: PAST_TS,
                receivedAt: PAST_TS
              },
              d_s7_143: {
                type: 'motion.detected',
                severity: 'warn',
                stream: 's7',
                seq: 143,
                source: 'device',
                reportedBy: 'dev1',
                deviceTs: PAST_TS + 1000,
                receivedAt: PAST_TS + 1000
              }
            }
          },
          dev_D3001: {
            // F2 (#51) & F6 (#39): pre-seeded pending command on revoked device
            commands: {
              cmd_30001: {
                type: 'light.set',
                params: { on: false },
                createdBy: 'uB',
                status: 'pending',
                createdAt: PAST_TS,
                ttlMs: 60000
              }
            }
          }
        }
      },
      home_C001: {
        meta: {
          name: 'Nha C',
          ownerUid: 'uC',
          createdAt: PAST_TS,
          schemaVersion: 1
        },
        members: {
          uC: { role: 'owner', addedAt: PAST_TS, addedBy: 'uC' }
        },
        devices: {
          dev_D2001: {
            name: 'Home C ESP32',
            model: 'esp32-smarthome',
            status: 'active',
            deviceAuthUid: 'dev2',
            createdAt: PAST_TS,
            createdBy: 'uC'
          }
        },
        deviceData: {
          dev_D2001: {
            state: {
              gateway: {
                online: true,
                reportedBy: 'uC',
                receivedAt: PAST_TS,
                values: { temp: 25, hum: 60 }
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
  await testEnv.clearDatabase();
  await seedBase();
});

// ============================================================================
// GROUP 1: Isolation & Positive Reads (#1 - #6, F5)
// ============================================================================
describe('Group 1: Isolation & Positive Reads (#1-#6, F5)', () => {
  it('#1 unauth cannot read or write any node (@AUTH)', async () => {
    const db = dbFor(null);
    await assertFails(db.ref('users/uA').once('value'));
    await assertFails(db.ref('userHomes/uA').once('value'));
    await assertFails(db.ref('homes/home_A001/meta').once('value'));
    await assertFails(
      db.ref('users/anon1').set({ displayName: 'Anon', createdAt: SERVER_TS })
    );
  });

  it('#2 uA cannot read cross-home home_C001 nodes (@MEMBER)', async () => {
    const db = dbFor('uA');
    await assertFails(db.ref('homes/home_C001/meta').once('value'));
    await assertFails(db.ref('homes/home_C001/members').once('value'));
    await assertFails(db.ref('homes/home_C001/devices').once('value'));
    await assertFails(
      db.ref('homes/home_C001/deviceData/dev_D2001/state/gateway').once('value')
    );
  });

  it('#3 stranger uD cannot read any home_A001 node (@MEMBER)', async () => {
    const db = dbFor('uD');
    await assertFails(db.ref('homes/home_A001/meta').once('value'));
    await assertFails(db.ref('homes/home_A001/members').once('value'));
    await assertFails(db.ref('homes/home_A001/devices').once('value'));
    await assertFails(
      db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').once('value')
    );
    await assertFails(
      db.ref('homes/home_A001/deviceData/dev_D1001/config').once('value')
    );
  });

  it('#4 stranger uD cannot write members/userHomes/state under home_A001', async () => {
    const db = dbFor('uD');
    await assertFails(
      db.ref('homes/home_A001/members/uD').set({
        role: 'member',
        addedAt: SERVER_TS,
        addedBy: 'uD'
      })
    );
    await assertFails(db.ref('userHomes/uD/home_A001').set('member'));
    await assertFails(
      db.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
        online: true,
        reportedBy: 'uD',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
  });

  it('#5 uB cannot read userHomes/uA or forge userHomes entries', async () => {
    const db = dbFor('uB');
    await assertFails(db.ref('userHomes/uA').once('value'));
    await assertFails(db.ref('userHomes/uB/home_C001').set('member'));
    await assertFails(db.ref('userHomes/uD/home_A001').set('member'));
  });

  it('#6a [F5] Owner uA can read meta, members, devices, state, config, telemetry, commands, acks, events', async () => {
    const db = dbFor('uA');
    const paths = [
      'homes/home_A001/meta',
      'homes/home_A001/members',
      'homes/home_A001/devices',
      'homes/home_A001/deviceData/dev_D1001/state/gateway',
      'homes/home_A001/deviceData/dev_D1001/state/device',
      'homes/home_A001/deviceData/dev_D1001/config',
      'homes/home_A001/deviceData/dev_D1001/telemetry',
      'homes/home_A001/deviceData/dev_D1001/commands',
      'homes/home_A001/deviceData/dev_D1001/acks',
      'homes/home_A001/deviceData/dev_D1001/events'
    ];
    for (const p of paths) {
      const snap = await assertSucceeds(db.ref(p).once('value'));
      assert.ok(snap.exists(), `Expected owner read to exist at ${p}`);
    }
  });

  it('#6b [F5] Member uB can read meta, members, devices, state, config, telemetry, commands, acks, events', async () => {
    const db = dbFor('uB');
    const paths = [
      'homes/home_A001/meta',
      'homes/home_A001/members',
      'homes/home_A001/devices',
      'homes/home_A001/deviceData/dev_D1001/state/gateway',
      'homes/home_A001/deviceData/dev_D1001/state/device',
      'homes/home_A001/deviceData/dev_D1001/config',
      'homes/home_A001/deviceData/dev_D1001/telemetry',
      'homes/home_A001/deviceData/dev_D1001/commands',
      'homes/home_A001/deviceData/dev_D1001/acks',
      'homes/home_A001/deviceData/dev_D1001/events'
    ];
    for (const p of paths) {
      const snap = await assertSucceeds(db.ref(p).once('value'));
      assert.ok(snap.exists(), `Expected member read to exist at ${p}`);
    }
  });
});

// ============================================================================
// GROUP 2: users/{uid} (#7 - #10, F3, ROW-12)
// ============================================================================
describe('Group 2: users/{uid} & optional updatedAt (#7-#10, F3, ROW-12)', () => {
  it('#7 uB creates own users/uB with displayName + createdAt: TIMESTAMP and reads it', async () => {
    const db = dbFor('uB');
    await assertSucceeds(
      db.ref('users/uB').set({ displayName: 'Member B', createdAt: SERVER_TS })
    );
    const snap = await assertSucceeds(db.ref('users/uB').once('value'));
    assert.equal(snap.val().displayName, 'Member B');
  });

  it('#8a uA updates users/uA keeping createdAt and refreshing updatedAt: TIMESTAMP', async () => {
    const db = dbFor('uA');
    await assertSucceeds(
      db.ref('users/uA').set({
        displayName: 'Owner A Updated',
        createdAt: PAST_TS,
        updatedAt: SERVER_TS
      })
    );
  });

  it('#8b [ROW-12][F3] users/uA partial update displayName without updating updatedAt and deleting optional updatedAt matches optional contract', async () => {
    // Pre-state: users/uA exists with createdAt = PAST_TS, updatedAt = PAST_TS
    // Target: verifies F3 optional contract — partial update on displayName succeeds without forcing updatedAt refresh,
    // and removing optional child updatedAt succeeds because .validate does not run on delete and parent .write has newData.exists().
    const db = dbFor('uA');
    await assertSucceeds(db.ref('users/uA/displayName').set('Owner A Partial'));
    const snapAfterPartial = await assertSucceeds(db.ref('users/uA').once('value'));
    assert.equal(snapAfterPartial.val().displayName, 'Owner A Partial');
    assert.equal(snapAfterPartial.val().updatedAt, PAST_TS);

    await assertSucceeds(db.ref('users/uA/updatedAt').remove());
    const snapAfterDel = await assertSucceeds(db.ref('users/uA').once('value'));
    assert.equal(snapAfterDel.val().updatedAt, undefined);
  });

  it('#9a uA cannot delete users/uA (blocked by newData.exists())', async () => {
    const db = dbFor('uA');
    await assertFails(db.ref('users/uA').remove());
  });

  it('#9b uA cannot overwrite users/uA/createdAt to a different value', async () => {
    const db = dbFor('uA');
    await assertFails(db.ref('users/uA/createdAt').set(SERVER_TS));
  });

  it('#10 uB and uD cannot read or write users/uA', async () => {
    await assertFails(dbFor('uB').ref('users/uA').once('value'));
    await assertFails(dbFor('uD').ref('users/uA').once('value'));
    await assertFails(dbFor('uB').ref('users/uA/displayName').set('Hack'));
    await assertFails(dbFor('uD').ref('users/uA/displayName').set('Hack'));
  });
});

// ============================================================================
// GROUP 3: Homes, Members, Revocation & Live Listener (#11 - #22, F1, F2, ROW-05, ROW-06, ROW-10)
// ============================================================================
describe('Group 3: Homes, Members & Live Listener Revocation (#11-#22)', () => {
  it('#11 uA creates new home home_N001 sequentially: meta -> members/uA -> userHomes/uA/home_N001', async () => {
    const db = dbFor('uA');
    await assertSucceeds(
      db.ref('homes/home_N001/meta').set({
        name: 'Nha Moi',
        ownerUid: 'uA',
        createdAt: SERVER_TS,
        schemaVersion: 1
      })
    );
    await assertSucceeds(
      db.ref('homes/home_N001/members/uA').set({
        role: 'owner',
        addedAt: SERVER_TS,
        addedBy: 'uA'
      })
    );
    await assertSucceeds(db.ref('userHomes/uA/home_N001').set('owner'));
  });

  it('#12 uA cannot create meta with wrong ownerUid, short $homeId, or schemaVersion=2', async () => {
    const db = dbFor('uA');
    await assertFails(
      db.ref('homes/home_N002/meta').set({
        name: 'Bad Owner',
        ownerUid: 'uB',
        createdAt: SERVER_TS,
        schemaVersion: 1
      })
    );
    await assertFails(
      db.ref('homes/1/meta').set({
        name: 'Short ID',
        ownerUid: 'uA',
        createdAt: SERVER_TS,
        schemaVersion: 1
      })
    );
    await assertFails(
      db.ref('homes/home_N003/meta').set({
        name: 'Bad Schema',
        ownerUid: 'uA',
        createdAt: SERVER_TS,
        schemaVersion: 2
      })
    );
  });

  it('#13 [ROW-10][F2] sibling update meta/name with createdAt in the past: uA ALLOW, uB DENY', async () => {
    // Pre-state: home_A001/meta/createdAt === PAST_TS (in the past).
    // Proves sibling update on meta/name is NOT blocked by createdAt/.validate.
    await assertSucceeds(dbFor('uA').ref('homes/home_A001/meta/name').set('Nha Chinh Renamed'));
    await assertFails(dbFor('uB').ref('homes/home_A001/meta/name').set('Member Rename'));
  });

  it('#14 uA/uB cannot modify meta/ownerUid or delete meta/home', async () => {
    await assertFails(dbFor('uA').ref('homes/home_A001/meta/ownerUid').set('uB'));
    await assertFails(dbFor('uB').ref('homes/home_A001/meta/ownerUid').set('uB'));
    await assertFails(dbFor('uA').ref('homes/home_A001/meta/ownerUid').remove());
    await assertFails(dbFor('uA').ref('homes/home_A001/meta').remove());
    await assertFails(dbFor('uA').ref('homes/home_A001').remove());
  });

  it('#15 uA adds member uB2 (members/uB2 then userHomes/uB2/home_A001)', async () => {
    const db = dbFor('uA');
    await assertSucceeds(
      db.ref('homes/home_A001/members/uB2').set({
        role: 'member',
        addedAt: SERVER_TS,
        addedBy: 'uA'
      })
    );
    await assertSucceeds(db.ref('userHomes/uB2/home_A001').set('member'));
  });

  it('#16 uA cannot add uB2 as owner, update existing members/uB, or add userHomes for non-member uD', async () => {
    const db = dbFor('uA');
    await assertFails(
      db.ref('homes/home_A001/members/uB2').set({
        role: 'owner',
        addedAt: SERVER_TS,
        addedBy: 'uA'
      })
    );
    await assertFails(
      db.ref('homes/home_A001/members/uB').set({
        role: 'member',
        addedAt: SERVER_TS,
        addedBy: 'uA'
      })
    );
    await assertFails(db.ref('userHomes/uD/home_A001').set('member'));
  });

  it('#17 uB cannot escalate own role to owner or add another member', async () => {
    const db = dbFor('uB');
    await assertFails(db.ref('homes/home_A001/members/uB/role').set('owner'));
    await assertFails(
      db.ref('homes/home_A001/members/uB2').set({
        role: 'member',
        addedAt: SERVER_TS,
        addedBy: 'uB'
      })
    );
  });

  it('#18 Owner uA cannot delete own membership or userHomes entry', async () => {
    const db = dbFor('uA');
    await assertFails(db.ref('homes/home_A001/members/uA').remove());
    await assertFails(db.ref('userHomes/uA/home_A001').remove());
  });

  it('#19 [ROW-05] uB self-leaves via multi-path atomic delete of members/uB and userHomes/uB/home_A001', async () => {
    const db = dbFor('uB');
    await assertSucceeds(
      db.ref('/').update({
        'homes/home_A001/members/uB': null,
        'userHomes/uB/home_A001': null
      })
    );
  });

  it('#20a [ROW-05] uA removes uB via multi-path atomic delete, then uB loses read/write access', async () => {
    await assertSucceeds(
      dbFor('uA').ref('/').update({
        'homes/home_A001/members/uB': null,
        'userHomes/uB/home_A001': null
      })
    );
    const dbB = dbFor('uB');
    await assertFails(dbB.ref('homes/home_A001/meta').once('value'));
    await assertFails(
      dbB.ref('homes/home_A001/deviceData/dev_D1001/state/gateway').once('value')
    );
    await assertFails(
      dbB.ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_00099').set({
        type: 'light.set',
        params: { on: true },
        createdBy: 'uB',
        status: 'pending',
        createdAt: SERVER_TS,
        ttlMs: 60000
      })
    );
  });

  it('#20b [ROW-06][F1] Live listener on homes/home_A001/meta receives PERMISSION_DENIED after uB membership is revoked', async () => {
    const dbB = dbFor('uB');
    const refB = dbB.ref('homes/home_A001/meta');

    let initialReceived = false;
    let revokedError = null;

    const initialPromise = new Promise((resolve, reject) => {
      const onError = (err) => {
        revokedError = err;
        if (!initialReceived) reject(err);
      };
      const onData = (snap) => {
        if (!initialReceived && snap.exists()) {
          initialReceived = true;
          resolve(snap.val());
        }
      };
      refB.on('value', onData, onError);
    });

    const initialMeta = await initialPromise;
    assert.equal(initialMeta.name, 'Nha Chinh A');

    // Now set up promise waiting for cancel error upon revocation
    refB.off('value');
    const cancelPromise = new Promise((resolve, reject) => {
      let gotFirst = false;
      const timer = setTimeout(() => {
        refB.off('value');
        reject(new Error('Timed out waiting for live listener PERMISSION_DENIED after revocation'));
      }, 5000);

      refB.on(
        'value',
        async () => {
          if (!gotFirst) {
            gotFirst = true;
            // Revoke uB while listener is actively attached
            try {
              await dbFor('uA').ref('/').update({
                'homes/home_A001/members/uB': null,
                'userHomes/uB/home_A001': null
              });
            } catch (e) {
              clearTimeout(timer);
              reject(e);
            }
          }
        },
        (err) => {
          clearTimeout(timer);
          resolve(err);
        }
      );
    });

    revokedError = await cancelPromise;
    refB.off('value');
    assert.ok(revokedError, 'Expected live listener error callback to fire');
    assert.match(
      String(revokedError.code || revokedError.message),
      /PERMISSION_DENIED|permission_denied/i
    );
  });

  it('#21 Deleting only members/uB leaves userHomes readable but blocks homes/home_A001/meta', async () => {
    await assertSucceeds(dbFor('uA').ref('homes/home_A001/members/uB').remove());
    const dbB = dbFor('uB');
    const uhSnap = await assertSucceeds(dbB.ref('userHomes/uB').once('value'));
    assert.equal(uhSnap.val().home_A001, 'member');
    await assertFails(dbB.ref('homes/home_A001/meta').once('value'));
  });

  it('#22 Member uB cannot delete another member uB2', async () => {
    await assertSucceeds(
      dbFor('uA').ref('homes/home_A001/members/uB2').set({
        role: 'member',
        addedAt: SERVER_TS,
        addedBy: 'uA'
      })
    );
    await assertFails(dbFor('uB').ref('homes/home_A001/members/uB2').remove());
  });
});

// ============================================================================
// GROUP 4: Devices & deviceAuthUid (#23 - #27, F2, F9, ROW-10)
// ============================================================================
describe('Group 4: Devices, deviceAuthUid & revoked terminal policy (#23-#27)', () => {
  it('#23a [ROW-10][F2] sibling update on devices/dev_D1001/name with createdAt in the past succeeds', async () => {
    await assertSucceeds(
      dbFor('uA').ref('homes/home_A001/devices/dev_D1001/name').set('Living Room v2')
    );
  });

  it('#23b uA creates dev_D4001, updates name, binds deviceAuthUid once, transitions status active->revoked', async () => {
    const db = dbFor('uA');
    await assertSucceeds(
      db.ref('homes/home_A001/devices/dev_D4001').set({
        name: 'Kitchen ESP32',
        model: 'esp32-smarthome',
        status: 'active',
        createdAt: SERVER_TS,
        createdBy: 'uA'
      })
    );
    await assertSucceeds(db.ref('homes/home_A001/devices/dev_D4001/name').set('Kitchen v2'));
    await assertSucceeds(db.ref('homes/home_A001/devices/dev_D4001/deviceAuthUid').set('dev4'));
    await assertSucceeds(db.ref('homes/home_A001/devices/dev_D4001/status').set('revoked'));
  });

  it('#23c [F9] uA cannot revert revoked device status back to active (terminal policy)', async () => {
    // Pre-state: dev_D3001 has status === 'revoked'.
    // Target: devices/$deviceId/status/.validate enforces one-way active -> revoked.
    await assertFails(dbFor('uA').ref('homes/home_A001/devices/dev_D3001/status').set('active'));
  });

  it('#24 uA cannot overwrite existing deviceAuthUid, delete deviceAuthUid, or delete device', async () => {
    const db = dbFor('uA');
    await assertFails(db.ref('homes/home_A001/devices/dev_D1001/deviceAuthUid').set('dev999'));
    await assertFails(db.ref('homes/home_A001/devices/dev_D1001/deviceAuthUid').remove());
    await assertFails(db.ref('homes/home_A001/devices/dev_D1001').remove());
  });

  it('#25 [F2] On unbound dev_D4002 (no deviceAuthUid yet), setting deviceAuthUid to "", uA (owner), or uB (member) hits .validate and is DENY', async () => {
    // Pre-state: dev_D4002 has NO deviceAuthUid, so .write (!data.exists()) passes and .validate is tested directly.
    const db = dbFor('uA');
    await assertFails(db.ref('homes/home_A001/devices/dev_D4002/deviceAuthUid').set(''));
    await assertFails(db.ref('homes/home_A001/devices/dev_D4002/deviceAuthUid').set('uA'));
    await assertFails(db.ref('homes/home_A001/devices/dev_D4002/deviceAuthUid').set('uB'));
    // Positive sanity check: valid unique device UID passes on the same fixture
    await assertSucceeds(db.ref('homes/home_A001/devices/dev_D4002/deviceAuthUid').set('dev4002'));
  });

  it('#26 Member uB cannot create or modify devices', async () => {
    const db = dbFor('uB');
    await assertFails(
      db.ref('homes/home_A001/devices/dev_D4001').set({
        name: 'Member Device',
        model: 'esp32',
        status: 'active',
        createdAt: SERVER_TS,
        createdBy: 'uB'
      })
    );
    await assertFails(db.ref('homes/home_A001/devices/dev_D1001/name').set('Hack'));
    await assertFails(db.ref('homes/home_A001/devices/dev_D1001/status').set('revoked'));
    await assertFails(db.ref('homes/home_A001/devices/dev_D4002/deviceAuthUid').set('dev4002'));
  });

  it('#27 uA cannot create device with short $deviceId or empty model', async () => {
    const db = dbFor('uA');
    await assertFails(
      db.ref('homes/home_A001/devices/d1').set({
        name: 'Short ID',
        model: 'esp32',
        status: 'active',
        createdAt: SERVER_TS,
        createdBy: 'uA'
      })
    );
    await assertFails(
      db.ref('homes/home_A001/devices/dev_D5001').set({
        name: 'Empty Model',
        model: '',
        status: 'active',
        createdAt: SERVER_TS,
        createdBy: 'uA'
      })
    );
  });
});

// ============================================================================
// GROUP 5: State & Partial Write & Scalar Validation (#28 - #33, F2, ROW-03, ROW-08)
// ============================================================================
describe('Group 5: State, Partial Write & hasChildren() (#28-#33)', () => {
  it('#28 uB writes full valid state/gateway', async () => {
    await assertSucceeds(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
        online: true,
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        deviceTs: Date.now(),
        values: { temp: 30, hum: 70, light: false, mode: 'HOME' }
      })
    );
  });

  it('#29 dev1 writes full valid state/device', async () => {
    await assertSucceeds(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
        online: true,
        reportedBy: 'dev1',
        receivedAt: SERVER_TS,
        deviceTs: Date.now(),
        values: { temp: 29, hum: 68, door: false, mode: 'HOME' }
      })
    );
  });

  it('#30a [ROW-03][F2] Same author uA partial-writing state/gateway/values/temp with past receivedAt is DENY (proves receivedAt === now at parent .validate)', async () => {
    // Pre-state: state/gateway has reportedBy='uA', receivedAt=PAST_TS.
    // Even though reportedBy matches auth.uid ('uA'), receivedAt !== now rejects partial write!
    await assertFails(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/state/gateway/values/temp').set(31)
    );
  });

  it('#30b [ROW-03][F2] Different member uB partial-writing state/gateway/values/temp is DENY (reportedBy + receivedAt)', async () => {
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/state/gateway/values/temp').set(31)
    );
  });

  it('#30c [ROW-03][F2] Device dev1 partial-writing state/device/values/temp with past receivedAt is DENY (proves state/device parent .validate)', async () => {
    // Pre-state: state/device has reportedBy='dev1', receivedAt=PAST_TS.
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/state/device/values/temp').set(31)
    );
  });

  it('#31 Namespace isolation: uA/uB cannot write state/device; dev1 cannot read/write state/gateway', async () => {
    await assertFails(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
        online: true,
        reportedBy: 'uA',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
        online: true,
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/state/gateway').once('value')
    );
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
        online: true,
        reportedBy: 'dev1',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
  });

  it('#32 Cross-device isolation: dev2 cannot write dev_D1001; dev1 cannot write dev_D2001', async () => {
    await assertFails(
      dbFor('dev2').ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
        online: true,
        reportedBy: 'dev2',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
    await assertFails(
      dbFor('dev1').ref('homes/home_C001/deviceData/dev_D2001/state/device').set({
        online: true,
        reportedBy: 'dev1',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
  });

  it('#33 [ROW-08] Zero-arg newData.hasChildren() rejects scalar values on state/gateway and state/device, plus range/extra-field checks', async () => {
    const dbB = dbFor('uB');
    const gwRef = dbB.ref('homes/home_A001/deviceData/dev_D1001/state/gateway');
    await assertFails(
      gwRef.set({
        online: true,
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: 'scalar'
      })
    );
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/state/device').set({
        online: true,
        reportedBy: 'dev1',
        receivedAt: SERVER_TS,
        values: 123
      })
    );
    await assertFails(
      gwRef.set({
        online: true,
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { mode: '' }
      })
    );
    await assertFails(
      gwRef.set({
        online: true,
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 999 }
      })
    );
    await assertFails(
      gwRef.set({
        online: true,
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 25 },
        extraField: 'nope'
      })
    );
    await assertFails(
      gwRef.set({
        online: true,
        reportedBy: 'uA',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
  });
});

// ============================================================================
// GROUP 6: Config (#34 - #36, F2, F8, ROW-03)
// ============================================================================
describe('Group 6: Config (#34-#36)', () => {
  it('#34 uA creates config (rev: 1) on new active device and increments to rev: 2', async () => {
    const db = dbFor('uA');
    const ref = db.ref('homes/home_A001/deviceData/dev_D4002/config');
    await assertSucceeds(
      ref.set({
        rev: 1,
        updatedBy: 'uA',
        updatedAt: SERVER_TS,
        tempLimit: 35,
        humLimit: 80,
        coldLimit: 18,
        autoAlarm: true
      })
    );
    await assertSucceeds(
      ref.set({
        rev: 2,
        updatedBy: 'uA',
        updatedAt: SERVER_TS,
        tempLimit: 36,
        humLimit: 75,
        coldLimit: 17,
        autoAlarm: false
      })
    );
  });

  it('#35 uB and dev1 cannot write config; dev1 can read config of dev_D1001', async () => {
    const payload = {
      rev: 2,
      updatedBy: 'uB',
      updatedAt: SERVER_TS,
      tempLimit: 35,
      humLimit: 80,
      coldLimit: 18,
      autoAlarm: true
    };
    await assertFails(dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/config').set(payload));
    await assertFails(
      dbFor('dev1')
        .ref('homes/home_A001/deviceData/dev_D1001/config')
        .set({ ...payload, updatedBy: 'dev1' })
    );
    await assertSucceeds(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/config').once('value')
    );
  });

  it('#36a [F2/F8] uA writing config with non-incrementing rev is DENY (isolated fixture)', async () => {
    await assertFails(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/config').set({
        rev: 1,
        updatedBy: 'uA',
        updatedAt: SERVER_TS,
        tempLimit: 35,
        humLimit: 80,
        coldLimit: 18,
        autoAlarm: true
      })
    );
  });

  it('#36b [F2/F8] uA writing config with out-of-range tempLimit=999 is DENY (isolated fixture)', async () => {
    await assertFails(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/config').set({
        rev: 2,
        updatedBy: 'uA',
        updatedAt: SERVER_TS,
        tempLimit: 999,
        humLimit: 80,
        coldLimit: 18,
        autoAlarm: true
      })
    );
  });

  it('#36c [ROW-03][F2/F8] uA partial-writing only config/tempLimit is DENY (parent config/.validate)', async () => {
    await assertFails(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/config/tempLimit').set(40)
    );
  });
});

// ============================================================================
// GROUP 7: Commands & ACKs (#37 - #43, F2, F6, F8, ROW-02, ROW-04, ROW-08)
// ============================================================================
describe('Group 7: Commands & ACKs (#37-#43)', () => {
  it('#37 uB creates valid pending command cmd_00010', async () => {
    await assertSucceeds(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_00010').set({
        type: 'light.set',
        params: { on: true },
        createdBy: 'uB',
        status: 'pending',
        createdAt: SERVER_TS,
        ttlMs: 60000
      })
    );
  });

  it('#38 [ROW-08] uB cannot create invalid commands (cancelled, forged createdBy, bad ttlMs, bad type, scalar params, empty mode, extra param, short cmdId)', async () => {
    const db = dbFor('uB');
    const base = {
      type: 'light.set',
      params: { on: true },
      createdBy: 'uB',
      status: 'pending',
      createdAt: SERVER_TS,
      ttlMs: 60000
    };
    const cmdPath = (id) => `homes/home_A001/deviceData/dev_D1001/commands/${id}`;

    await assertFails(db.ref(cmdPath('cmd_bad01')).set({ ...base, status: 'cancelled' }));
    await assertFails(db.ref(cmdPath('cmd_bad02')).set({ ...base, createdBy: 'uA' }));
    await assertFails(db.ref(cmdPath('cmd_bad03')).set({ ...base, ttlMs: 999999 }));
    await assertFails(db.ref(cmdPath('cmd_bad04')).set({ ...base, type: 'reboot.all' }));
    await assertFails(db.ref(cmdPath('cmd_bad05')).set({ ...base, params: 123 }));
    await assertFails(db.ref(cmdPath('cmd_bad06')).set({ ...base, params: { mode: '' } }));
    await assertFails(db.ref(cmdPath('cmd_bad07')).set({ ...base, params: { hack: true } }));
    await assertFails(db.ref(cmdPath('c1')).set(base));
  });

  it('#39a [ROW-02][F6/F8] uB cancels own pending un-ACKed command on active device (verifies data.parent() + @ACTIVE)', async () => {
    await assertSucceeds(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_00001/status').set('cancelled')
    );
  });

  it('#39b [ROW-02][F8] uA cannot cancel uB command (data.parent().child(createdBy) !== auth.uid)', async () => {
    await assertFails(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_00001/status').set('cancelled')
    );
  });

  it('#39c [F8] uB cannot cancel own command once ACK exists (cmd_00002 has ACK)', async () => {
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_00002/status').set('cancelled')
    );
  });

  it('#39d [F6][F8] uB cannot cancel own pending un-ACKed command on REVOKED device dev_D3001 (@ACTIVE at status/.write)', async () => {
    // Pre-state: dev_D3001 is revoked and has cmd_30001 (createdBy='uB', status='pending', no ACK).
    // Only @ACTIVE fails — proves F6 fix!
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D3001/commands/cmd_30001/status').set('cancelled')
    );
  });

  it('#40 dev1 reads commands and acks of dev_D1001 (ALLOW) and cannot write commands (DENY)', async () => {
    const db = dbFor('dev1');
    await assertSucceeds(db.ref('homes/home_A001/deviceData/dev_D1001/commands').once('value'));
    await assertSucceeds(db.ref('homes/home_A001/deviceData/dev_D1001/acks').once('value'));
    await assertFails(
      db.ref('homes/home_A001/deviceData/dev_D1001/commands/cmd_dev01').set({
        type: 'light.set',
        params: { on: true },
        createdBy: 'dev1',
        status: 'pending',
        createdAt: SERVER_TS,
        ttlMs: 60000
      })
    );
  });

  it('#41 dev1 creates valid ACK for existing cmd_00001 on dev_D1001', async () => {
    await assertSucceeds(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/acks/cmd_00001').set({
        cmdId: 'cmd_00001',
        result: 'ok',
        reportedBy: 'dev1',
        receivedAt: SERVER_TS
      })
    );
  });

  it('#42a [ROW-04][F2/F8] dev1 cannot overwrite or delete existing ACK cmd_00002 (create-only)', async () => {
    const db = dbFor('dev1');
    await assertFails(
      db.ref('homes/home_A001/deviceData/dev_D1001/acks/cmd_00002').set({
        cmdId: 'cmd_00002',
        result: 'failed',
        reportedBy: 'dev1',
        receivedAt: SERVER_TS
      })
    );
    await assertFails(db.ref('homes/home_A001/deviceData/dev_D1001/acks/cmd_00002').remove());
  });

  it('#42b [F2/F8] dev1 cannot create ACK for nonexistent command cmd_99999', async () => {
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/acks/cmd_99999').set({
        cmdId: 'cmd_99999',
        result: 'ok',
        reportedBy: 'dev1',
        receivedAt: SERVER_TS
      })
    );
  });

  it('#43 Spoofed ACK: uA, uB, and dev2 cannot write acks/cmd_00001 on dev_D1001', async () => {
    for (const actor of ['uA', 'uB', 'dev2']) {
      await assertFails(
        dbFor(actor).ref('homes/home_A001/deviceData/dev_D1001/acks/cmd_00001').set({
          cmdId: 'cmd_00001',
          result: 'ok',
          reportedBy: actor,
          receivedAt: SERVER_TS
        })
      );
    }
  });
});

// ============================================================================
// GROUP 8: Telemetry & Events (#44 - #50, F2, F8, ROW-04, ROW-07, ROW-09)
// ============================================================================
describe('Group 8: Telemetry, Events & History Cleanup (#44-#50)', () => {
  it('#44 uB writes telemetry/*_g and dev1 writes telemetry/*_d', async () => {
    await assertSucceeds(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201431_g').set({
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 29, hum: 66 }
      })
    );
    await assertSucceeds(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201431_d').set({
        source: 'device',
        reportedBy: 'dev1',
        receivedAt: SERVER_TS,
        values: { temp: 29, hum: 66 }
      })
    );
  });

  it('#45a [ROW-09][F2] Telemetry source mismatch reaches .validate: uB writes _g with source=device -> DENY', async () => {
    // Passes .write (_g + @MEMBER + @ACTIVE + !data.exists()); rejected specifically by .validate source/suffix check!
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201431_g').set({
        source: 'device',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 28, hum: 65 }
      })
    );
  });

  it('#45b [ROW-09][F2] Telemetry source mismatch reaches .validate: dev1 writes _d with source=gateway -> DENY', async () => {
    // Passes .write (_d + @DEVICE + @ACTIVE + !data.exists()); rejected specifically by .validate source/suffix check!
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201431_d').set({
        source: 'gateway',
        reportedBy: 'dev1',
        receivedAt: SERVER_TS,
        values: { temp: 28, hum: 65 }
      })
    );
  });

  it('#45c Telemetry namespace & schema rejections (uB->_d, wrong reportedBy, scalar values, bad temp, dev1->_g, dev1 read)', async () => {
    const base = 'homes/home_A001/deviceData/dev_D1001/telemetry';
    await assertFails(
      dbFor('uB').ref(`${base}/202611201432_d`).set({
        source: 'device',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 28, hum: 65 }
      })
    );
    await assertFails(
      dbFor('uB').ref(`${base}/202611201433_g`).set({
        source: 'gateway',
        reportedBy: 'uA',
        receivedAt: SERVER_TS,
        values: { temp: 28, hum: 65 }
      })
    );
    await assertFails(
      dbFor('uB').ref(`${base}/202611201434_g`).set({
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: 'bad'
      })
    );
    await assertFails(
      dbFor('uB').ref(`${base}/202611201435_g`).set({
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 999, hum: 65 }
      })
    );
    await assertFails(
      dbFor('dev1').ref(`${base}/202611201436_g`).set({
        source: 'gateway',
        reportedBy: 'dev1',
        receivedAt: SERVER_TS,
        values: { temp: 28, hum: 65 }
      })
    );
    await assertFails(dbFor('dev1').ref(base).once('value'));
  });

  it('#46 [ROW-07] uB writes events/g_s7_150 and dev1 writes events/d_s7_150 (string+number concat & %1===0)', async () => {
    await assertSucceeds(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_150').set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 150,
        source: 'gateway',
        reportedBy: 'uB',
        deviceTs: Date.now(),
        receivedAt: SERVER_TS
      })
    );
    await assertSucceeds(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/events/d_s7_150').set({
        type: 'motion.detected',
        severity: 'warn',
        stream: 's7',
        seq: 150,
        source: 'device',
        reportedBy: 'dev1',
        deviceTs: Date.now(),
        receivedAt: SERVER_TS
      })
    );
  });

  it('#47a [ROW-04][F8] Duplicate create-only write on events/g_s7_142 is DENY (isolated fixture)', async () => {
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_142').set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 142,
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS
      })
    );
  });

  it('#47b [ROW-07][F8] Non-integer seq=1.5 is rejected by (val() % 1 === 0) and eventId mismatch', async () => {
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_151').set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 1.5,
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS
      })
    );
  });

  it('#47c [F8] Events validation rejects d_ prefix by uB, source mismatch, invalid stream, empty type, wrong reportedBy', async () => {
    const base = 'homes/home_A001/deviceData/dev_D1001/events';
    const valid = {
      type: 'door.opened',
      severity: 'info',
      stream: 's7',
      seq: 160,
      source: 'gateway',
      reportedBy: 'uB',
      receivedAt: SERVER_TS
    };
    await assertFails(dbFor('uB').ref(`${base}/d_s7_160`).set({ ...valid, source: 'device' }));
    await assertFails(dbFor('uB').ref(`${base}/g_s7_160`).set({ ...valid, source: 'device' }));
    await assertFails(dbFor('uB').ref(`${base}/g_s7_161`).set({ ...valid, seq: 161, stream: 'bad key' }));
    await assertFails(dbFor('uB').ref(`${base}/g_s7_162`).set({ ...valid, seq: 162, type: '' }));
    await assertFails(dbFor('uB').ref(`${base}/g_s7_163`).set({ ...valid, seq: 163, reportedBy: 'uA' }));
  });

  it('#48 dev1 cannot write events/g_* or read events', async () => {
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_170').set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 170,
        source: 'gateway',
        reportedBy: 'dev1',
        receivedAt: SERVER_TS
      })
    );
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/events').once('value')
    );
  });

  it('#49a [F2] Owner uA deletes exact leaf $recordId and $eventId (ALLOW), not parent node', async () => {
    await assertSucceeds(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201430_g').remove()
    );
    await assertSucceeds(
      dbFor('uA').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_142').remove()
    );
  });

  it('#49b [F2] Member uB and device dev1 cannot delete exact leaf $recordId or $eventId (DENY)', async () => {
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201430_g').remove()
    );
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_142').remove()
    );
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/telemetry/202611201430_d').remove()
    );
    await assertFails(
      dbFor('dev1').ref('homes/home_A001/deviceData/dev_D1001/events/d_s7_143').remove()
    );
  });

  it('#50 Device dev1 cannot read user or home metadata nodes', async () => {
    const db = dbFor('dev1');
    await assertFails(db.ref('users/uA').once('value'));
    await assertFails(db.ref('userHomes/uA').once('value'));
    await assertFails(db.ref('homes/home_A001/meta').once('value'));
    await assertFails(db.ref('homes/home_A001/members').once('value'));
    await assertFails(db.ref('homes/home_A001/devices').once('value'));
  });
});

// ============================================================================
// GROUP 9: Revoked Device, deviceKey Guard, Timestamp & Index Queries (#51 - #55, F2, F5, ROW-01, ROW-11)
// ============================================================================
describe('Group 9: Revoked Device, deviceKey Guard, Timestamp & Queries (#51-#55)', () => {
  it('#51 [F2] Revoked device dev3 cannot read config/commands/acks or write state/telemetry/acks(pre-seeded cmd_30001)/events', async () => {
    // Pre-state: dev_D3001/commands/cmd_30001 is pre-seeded so acks/cmd_30001 reaches @DEVICE (@ACTIVE=false).
    const db = dbFor('dev3');
    const base = 'homes/home_A001/deviceData/dev_D3001';
    await assertFails(db.ref(`${base}/config`).once('value'));
    await assertFails(db.ref(`${base}/commands`).once('value'));
    await assertFails(db.ref(`${base}/acks`).once('value'));
    await assertFails(
      db.ref(`${base}/state/device`).set({
        online: true,
        reportedBy: 'dev3',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
    await assertFails(
      db.ref(`${base}/telemetry/202611201431_d`).set({
        source: 'device',
        reportedBy: 'dev3',
        receivedAt: SERVER_TS,
        values: { temp: 25, hum: 60 }
      })
    );
    await assertFails(
      db.ref(`${base}/acks/cmd_30001`).set({
        cmdId: 'cmd_30001',
        result: 'ok',
        reportedBy: 'dev3',
        receivedAt: SERVER_TS
      })
    );
    await assertFails(
      db.ref(`${base}/events/d_s7_300`).set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 300,
        source: 'device',
        reportedBy: 'dev3',
        receivedAt: SERVER_TS
      })
    );
  });

  it('#52 [F2/F8] uA/uB cannot write state/gateway, config, telemetry, commands, events on revoked dev_D3001', async () => {
    const base = 'homes/home_A001/deviceData/dev_D3001';
    await assertFails(
      dbFor('uB').ref(`${base}/state/gateway`).set({
        online: true,
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 25 }
      })
    );
    await assertFails(
      dbFor('uA').ref(`${base}/config`).set({
        rev: 1,
        updatedBy: 'uA',
        updatedAt: SERVER_TS,
        tempLimit: 35,
        humLimit: 80,
        coldLimit: 18,
        autoAlarm: true
      })
    );
    await assertFails(
      dbFor('uB').ref(`${base}/telemetry/202611201431_g`).set({
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        values: { temp: 25, hum: 60 }
      })
    );
    await assertFails(
      dbFor('uB').ref(`${base}/commands/cmd_30002`).set({
        type: 'light.set',
        params: { on: true },
        createdBy: 'uB',
        status: 'pending',
        createdAt: SERVER_TS,
        ttlMs: 60000
      })
    );
    await assertFails(
      dbFor('uB').ref(`${base}/events/g_s7_301`).set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 301,
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS
      })
    );
  });

  it('#53 [F2] deviceKey guard: literal in meta/name, devices/name, events/detail AND full new event payload with extra field deviceKey -> DENY', async () => {
    await assertFails(
      dbFor('uA').ref('homes/home_A001/meta/name').set('My deviceKey home')
    );
    await assertFails(
      dbFor('uA').ref('homes/home_A001/devices/dev_D1001/name').set('ESP deviceKey')
    );
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_201').set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 201,
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        detail: 'leaked deviceKey=abc'
      })
    );
    // F2 (#53): complete valid new event payload + extra field `deviceKey` -> hits $other: false!
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/events/g_s7_202').set({
        type: 'door.opened',
        severity: 'info',
        stream: 's7',
        seq: 202,
        source: 'gateway',
        reportedBy: 'uB',
        receivedAt: SERVER_TS,
        deviceKey: 'secret-lan-key-123'
      })
    );
  });

  it('#54 [ROW-01] Client clock offsets -10s and +60s are rejected (now === ServerValue.TIMESTAMP)', async () => {
    const minus10s = Date.now() - 10_000;
    const plus60s = Date.now() + 60_000;

    // createdAt on home meta
    await assertFails(
      dbFor('uA').ref('homes/home_T001/meta').set({
        name: 'Clock Past',
        ownerUid: 'uA',
        createdAt: minus10s,
        schemaVersion: 1
      })
    );
    await assertFails(
      dbFor('uA').ref('homes/home_T002/meta').set({
        name: 'Clock Future',
        ownerUid: 'uA',
        createdAt: plus60s,
        schemaVersion: 1
      })
    );
    // receivedAt on state/gateway
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
        online: true,
        reportedBy: 'uB',
        receivedAt: minus10s,
        values: { temp: 25 }
      })
    );
    await assertFails(
      dbFor('uB').ref('homes/home_A001/deviceData/dev_D1001/state/gateway').set({
        online: true,
        reportedBy: 'uB',
        receivedAt: plus60s,
        values: { temp: 25 }
      })
    );
    // updatedAt on users/uA
    await assertFails(dbFor('uA').ref('users/uA/updatedAt').set(minus10s));
    await assertFails(dbFor('uA').ref('users/uA/updatedAt').set(plus60s));
    // addedAt on members/uB2
    await assertFails(
      dbFor('uA').ref('homes/home_A001/members/uB2').set({
        role: 'member',
        addedAt: plus60s,
        addedBy: 'uA'
      })
    );
  });

  it('#55 [ROW-11][F5] Indexed queries on events (receivedAt), commands (createdAt), and telemetry (orderByKey.limitToLast)', async () => {
    for (const uid of ['uA', 'uB']) {
      const db = dbFor(uid);
      const eventsSnap = await assertSucceeds(
        db
          .ref('homes/home_A001/deviceData/dev_D1001/events')
          .orderByChild('receivedAt')
          .limitToLast(5)
          .once('value')
      );
      assert.equal(Object.keys(eventsSnap.val() || {}).length, 2);

      const cmdsSnap = await assertSucceeds(
        db
          .ref('homes/home_A001/deviceData/dev_D1001/commands')
          .orderByChild('createdAt')
          .startAt(0)
          .once('value')
      );
      assert.equal(Object.keys(cmdsSnap.val() || {}).length, 2);

      const telSnap = await assertSucceeds(
        db
          .ref('homes/home_A001/deviceData/dev_D1001/telemetry')
          .orderByKey()
          .limitToLast(1)
          .once('value')
      );
      assert.equal(Object.keys(telSnap.val() || {}).length, 1);
    }
  });
});
