import { actor, browser, input, write } from './support.mjs';
import { verifyEarly } from './early-checks.mjs';

const report = { sourceRevision: input.sourceRevision, jarSha256: input.jarSha256,
  automated: true, manual: 'NOT_RUN', cases: [] };
try {
  const admin = await actor('preview-admin@example.test');
  const manager = await actor('pilot-manager@example.test');
  for (const count of [9, 16]) {
    report.cases.push(await verifyEarly(admin, manager, count));
    console.log('Ранний плей-офф ' + count + ': сохранение пары, очередь и завершение проверены');
  }
  report.result = 'PASS';
} catch (error) {
  report.result = 'FAIL';
  const authorization = Buffer.from('preview:' + input.gatewayPassword).toString('base64');
  report.failure = [input.password, input.gatewayPassword, authorization].reduce(
    (message, secret) => message.replaceAll(secret, '[REDACTED]'), error.message);
  console.error(report.failure);
  process.exitCode = 1;
} finally {
  await write('extended-report', report);
  await browser.close();
}
