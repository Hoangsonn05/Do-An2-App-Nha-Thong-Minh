import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rulesDir = path.resolve(__dirname, '..');

const DEFAULT_JBR21 = 'D:\\Program Files\\Android Studio\\jbr';
const javaHome =
  process.env.FIREBASE_JAVA_HOME ||
  process.env.ANDROID_STUDIO_JBR ||
  DEFAULT_JBR21;

const javaBin = path.join(javaHome, 'bin');
const javaExe = path.join(javaBin, process.platform === 'win32' ? 'java.exe' : 'java');

if (!fs.existsSync(javaExe)) {
  console.error(`[run-emulator] Java executable not found at: ${javaExe}`);
  console.error('[run-emulator] Set FIREBASE_JAVA_HOME or ANDROID_STUDIO_JBR to a valid JDK 21 installation.');
  process.exit(1);
}

const firebaseEntry = path.join(rulesDir, 'node_modules', 'firebase-tools', 'lib', 'bin', 'firebase.js');
if (!fs.existsSync(firebaseEntry)) {
  console.error(`[run-emulator] firebase-tools not installed at: ${firebaseEntry}`);
  console.error('[run-emulator] Run "npm install" inside cloud-rules/ first.');
  process.exit(1);
}

const extraArgs = process.argv.slice(2);
const testCommand = extraArgs.length > 0 ? extraArgs.join(' ') : 'node --test test/rules.test.mjs';

const env = {
  ...process.env,
  JAVA_HOME: javaHome,
  PATH: `${javaBin}${path.delimiter}${process.env.PATH || ''}`
};

const args = [
  firebaseEntry,
  'emulators:exec',
  '--project',
  'demo-smarthomeai',
  '--only',
  'database',
  testCommand
];

console.log(`[run-emulator] JAVA_HOME=${javaHome}`);
console.log(`[run-emulator] Project=demo-smarthomeai (local emulator only)`);
console.log(`[run-emulator] Command=${testCommand}`);

const child = spawn(process.execPath, args, {
  cwd: rulesDir,
  env,
  stdio: 'inherit'
});

const forwardSignal = (signal) => {
  if (!child.killed) {
    child.kill(signal);
  }
};

process.on('SIGINT', () => forwardSignal('SIGINT'));
process.on('SIGTERM', () => forwardSignal('SIGTERM'));

child.on('error', (err) => {
  console.error('[run-emulator] Failed to start Firebase Emulator:', err.message);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  if (signal) {
    console.error(`[run-emulator] Emulator process terminated by signal ${signal}`);
    process.exit(1);
  }
  process.exit(code ?? 1);
});
