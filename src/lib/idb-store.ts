/**
 * A very small promise wrapper over one IndexedDB object store.
 *
 * Written by hand rather than pulling in `idb` (~2kb but a dependency) because
 * this needs exactly four operations and no schema evolution beyond version 1.
 *
 * Why not keep using localStorage: it is synchronous and shared across the
 * whole origin, so unrelated code filling the ~5MB quota makes `setItem` throw
 * — and for a reward-critical queue that means a completed session's XP is
 * silently dropped. IndexedDB has a far larger quota and surfaces failures as
 * rejected promises that can be observed and retried.
 */

const DB_NAME = "stackd";
const DB_VERSION = 1;

function openDb(store: string): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") {
      reject(new Error("indexeddb_unavailable"));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(store)) {
        db.createObjectStore(store, { keyPath: "id" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("indexeddb_open_failed"));
    // Another tab holds an old version open. Fail rather than hang forever;
    // the caller falls back to localStorage.
    req.onblocked = () => reject(new Error("indexeddb_blocked"));
  });
}

function tx<T>(
  store: string,
  mode: IDBTransactionMode,
  run: (s: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  return openDb(store).then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(store, mode);
        const req = run(t.objectStore(store));
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error ?? new Error("indexeddb_tx_failed"));
        t.oncomplete = () => db.close();
      }),
  );
}

/**
 * Only the key matters here. Deliberately not intersected with
 * `Record<string, unknown>` — a declared interface does not satisfy an index
 * signature, so that would reject exactly the typed payloads this exists for.
 */
export type IdbRecord = { id: string };

export const idb = {
  async getAll<T extends IdbRecord>(store: string): Promise<T[]> {
    return (await tx<T[]>(store, "readonly", (s) => s.getAll() as IDBRequest<T[]>)) ?? [];
  },
  async put<T extends IdbRecord>(store: string, value: T): Promise<void> {
    await tx(store, "readwrite", (s) => s.put(value));
  },
  async remove(store: string, id: string): Promise<void> {
    await tx(store, "readwrite", (s) => s.delete(id));
  },
  async available(): Promise<boolean> {
    try {
      await openDb("finalize-queue").then((db) => db.close());
      return true;
    } catch {
      return false;
    }
  },
};
