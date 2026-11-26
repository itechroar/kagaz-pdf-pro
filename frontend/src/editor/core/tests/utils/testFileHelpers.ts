/**
 * Test utilities for creating KagazFile objects in tests
 */

import { rememberPdfAccess } from "@editor/services/pdfAccessService";
import { KagazFile, createKagazFile } from "@editor/types/fileContext";

/**
 * Create a KagazFile object for testing purposes
 */
export function createTestKagazFile(
  name: string,
  content: string = "test content",
  type: string = "application/pdf",
): KagazFile {
  const file = new File([content], name, { type });
  rememberUnprotectedPdf(file);
  return createKagazFile(file);
}

/**
 * Create multiple KagazFile objects for testing
 */
export function createTestFilesWithId(
  files: Array<{ name: string; content?: string; type?: string }>,
): KagazFile[] {
  return files.map(
    ({ name, content = "test content", type = "application/pdf" }) =>
      createTestKagazFile(name, content, type),
  );
}

/** Marks synthetic, unencrypted test bytes as already inspected at admission. */
export function rememberUnprotectedPdf(file: File): File {
  rememberPdfAccess(file, {
    password: "",
    encrypted: false,
    signed: false,
    ownerAuthenticated: false,
    permissions: -4,
    pageCount: 1,
  });
  return file;
}
