import "fake-indexeddb/auto";
import { afterEach, describe, expect, it } from "vitest";
import {
  fileStorage,
  type StoredKagazFileRecord,
} from "@editor/services/fileStorage";
import {
  indexedDBManager,
  DATABASE_CONFIGS,
} from "@editor/services/indexedDBManager";
import {
  createNewKagazFileStub,
  createKagazFile,
} from "@editor/types/fileContext";
import {
  clearPdfAccess,
  getPdfAccess,
  rememberPdfAccess,
} from "@editor/services/pdfAccessService";

afterEach(async () => {
  clearPdfAccess();
  await fileStorage.clearAll();
});

describe("stored PDF protection metadata", () => {
  it("reading a saved copy does not revoke the active document's session", async () => {
    const file = new File(["encrypted bytes"], "private.pdf", {
      type: "application/pdf",
    });
    const stub = createNewKagazFileStub(file);
    const original = createKagazFile(file, stub.id);
    rememberPdfAccess(original, {
      password: "secret",
      encrypted: true,
      signed: false,
      ownerAuthenticated: true,
      permissions: -4,
      pageCount: 1,
    });
    await fileStorage.storeKagazFile(original, stub);
    const copy = await fileStorage.getKagazFile(stub.id);
    expect(copy).not.toBe(original);
    expect(getPdfAccess(stub.id)?.password).toBe("secret");
    expect(getPdfAccess(copy)).toBeUndefined();
  });
  it("retains encryption through all library read paths without retaining session access", async () => {
    const file = new File(["encrypted bytes"], "private.pdf", {
      type: "application/pdf",
    });
    const stub = createNewKagazFileStub(file);
    stub.processedFile = {
      pages: [],
      isEncrypted: true,
      requiresOpeningPassword: true,
    };
    stub.thumbnailUrl = "data:image/png;base64,private";
    await fileStorage.storeKagazFile(
      createKagazFile(file, stub.id),
      stub,
    );
    clearPdfAccess();
    const reads = [
      await fileStorage.getKagazFileStub(stub.id),
      ...(await fileStorage.getAllKagazFileStubs()),
      ...(await fileStorage.getLeafKagazFileStubs()),
    ];
    expect(reads).toHaveLength(3);
    for (const read of reads)
      expect(read?.processedFile).toMatchObject({
        isEncrypted: true,
        requiresOpeningPassword: true,
      });
  });

  it("discards legacy PDF previews whose encryption status was never stored", async () => {
    const file = new File(["protected legacy bytes"], "legacy.pdf", {
      type: "application/pdf",
    });
    const stub = createNewKagazFileStub(file);
    const record: StoredKagazFileRecord = {
      ...stub,
      fileId: stub.id,
      quickKey: "legacy",
      data: new Uint8Array([1, 2]).buffer,
      thumbnail: "data:image/png;base64,private",
      thumbnailStoredAt: Date.now(),
    };
    const db = await indexedDBManager.openDatabase(DATABASE_CONFIGS.FILES);
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction("files", "readwrite");
      transaction.objectStore("files").put(record);
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
    const reads = [
      await fileStorage.getKagazFileStub(stub.id),
      ...(await fileStorage.getAllKagazFileStubs()),
      ...(await fileStorage.getLeafKagazFileStubs()),
    ];
    for (const read of reads) expect(read?.thumbnailUrl).toBeUndefined();
  });
});
