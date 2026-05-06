// Minimal External Task worker for Camunda 7 (learning/demo)
// Requires Node.js 18+ (built-in fetch)

const baseUrl = process.env.CAMUNDA_REST ?? 'http://localhost:8090/engine-rest';
const topic = process.env.TOPIC ?? 'demo-worker';
const workerId = process.env.WORKER_ID ?? 'node-worker-1';

const pollIntervalMs = Number(process.env.POLL_INTERVAL_MS ?? '1000');

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchAndLock() {
  const res = await fetch(`${baseUrl}/external-task/fetchAndLock`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      workerId,
      maxTasks: 1,
      usePriority: true,
      topics: [
        {
          topicName: topic,
          lockDuration: 10000,
          variables: ['demoInput'],
        },
      ],
    }),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`fetchAndLock failed: ${res.status} ${text}`);
  }

  return await res.json();
}

async function complete(taskId, lockKey, variables) {
  const res = await fetch(`${baseUrl}/external-task/${encodeURIComponent(taskId)}/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      workerId,
      variables,
    }),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`complete failed: ${res.status} ${text}`);
  }
}

async function main() {
  // eslint-disable-next-line no-console
  console.log(`Worker starting: baseUrl=${baseUrl} topic=${topic} workerId=${workerId}`);

  while (true) {
    try {
      const tasks = await fetchAndLock();
      if (!Array.isArray(tasks) || tasks.length === 0) {
        await sleep(pollIntervalMs);
        continue;
      }

      const t = tasks[0];
      // eslint-disable-next-line no-console
      console.log(`Got task: id=${t.id} topic=${t.topicName} businessKey=${t.businessKey ?? ''}`);

      // Do work here (learning: just set an output variable)
      await complete(t.id, t.lockExpirationTime, {
        demoOutput: { value: `done-by-${workerId}`, type: 'String' },
      });

      // eslint-disable-next-line no-console
      console.log(`Completed task: id=${t.id}`);
    } catch (err) {
      // eslint-disable-next-line no-console
      console.error(err);
      await sleep(Math.max(pollIntervalMs, 1000));
    }
  }
}

await main();
