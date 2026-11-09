/**
 * Cross-platform runner script with built-in HTTP health check polling.
 * Ensures the Spring Boot backend on port 8080 is live and healthy
 * before launching the Vite frontend on port 3000 to prevent ECONNREFUSED.
 */
import { spawn } from 'child_process';
import http from 'http';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const HEALTH_URL = 'http://127.0.0.1:8080/actuator/health';
const POLL_INTERVAL_MS = 1500;
const MAX_ATTEMPTS = 60; // 90 seconds timeout

function checkBackendHealth() {
  return new Promise((resolve) => {
    const req = http.get(HEALTH_URL, (res) => {
      if (res.statusCode === 200 || res.statusCode === 401) {
        // 200 (healthy) or 401 (active Spring Security) indicates port 8080 is up and accepting HTTP traffic
        resolve(true);
      } else {
        resolve(false);
      }
    });

    req.on('error', () => {
      resolve(false);
    });

    req.setTimeout(1000, () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function waitForBackend() {
  console.log('\x1b[36m%s\x1b[0m', '⏳ Waiting for Spring Boot Backend on port 8080 to become ready...');
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    const isUp = await checkBackendHealth();
    if (isUp) {
      console.log('\x1b[32m%s\x1b[0m', '✅ Spring Boot Backend is UP and accepting requests!');
      return true;
    }
    process.stdout.write(`\x1b[33m.\x1b[0m`);
    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
  }
  console.log('\n\x1b[31m%s\x1b[0m', '❌ Timed out waiting for Spring Boot backend.');
  return false;
}

function startBackend() {
  console.log('\x1b[34m%s\x1b[0m', '🚀 Launching Spring Boot Backend...');
  // Check for mvn.cmd on Windows, else mvn
  const isWindows = process.platform === 'win32';
  const mvnCommand = isWindows ? 'mvn.cmd' : 'mvn';

  const backendProcess = spawn(mvnCommand, ['spring-boot:run'], {
    cwd: __dirname,
    stdio: 'inherit',
    shell: true,
  });

  backendProcess.on('error', (err) => {
    console.error('\x1b[31m%s\x1b[0m', `Failed to start Spring Boot with '${mvnCommand}': ${err.message}`);
    console.log('Ensure Maven is on your system PATH, or use start.ps1 / start.bat');
  });

  return backendProcess;
}

function startFrontend() {
  console.log('\n\x1b[35m%s\x1b[0m', '⚡ Launching React Vite Frontend (http://localhost:3000)...');
  const isWindows = process.platform === 'win32';
  const npmCmd = isWindows ? 'npm.cmd' : 'npm';

  const frontendProcess = spawn(npmCmd, ['run', 'dev'], {
    cwd: path.join(__dirname, 'frontend'),
    stdio: 'inherit',
    shell: true,
  });

  frontendProcess.on('error', (err) => {
    console.error('\x1b[31m%s\x1b[0m', `Failed to start Frontend: ${err.message}`);
  });

  return frontendProcess;
}

async function main() {
  const isFrontendOnly = process.argv.includes('--frontend-only');

  if (!isFrontendOnly) {
    const backend = startBackend();
    process.on('SIGINT', () => {
      backend.kill('SIGINT');
      process.exit();
    });
  }

  const backendReady = await waitForBackend();
  if (backendReady) {
    const frontend = startFrontend();
    process.on('SIGINT', () => {
      frontend.kill('SIGINT');
      process.exit();
    });
  } else {
    console.log('Skipping frontend startup because backend failed to start.');
  }
}

main().catch(console.error);
