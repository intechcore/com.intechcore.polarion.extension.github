/**
 * Runs `task` for every value, at most `limit` at a time, and resolves when all are done. A task that
 * fails rejects the whole run, so a task catches what it can handle itself.
 */
export async function eachLimited<T>(values: T[], limit: number, task: (value: T) => Promise<void>): Promise<void> {
  let next = 0;
  const worker = async () => {
    while (next < values.length) {
      await task(values[next++]);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, values.length) }, worker));
}
