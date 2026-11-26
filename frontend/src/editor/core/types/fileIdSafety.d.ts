/**
 * Type safety declarations to prevent file.name/UUID confusion
 */

import { FileId, KagazFile } from "@editor/types/fileContext";

declare global {
  namespace FileIdSafety {
    // Mark functions that should never accept file.name as parameters
    type SafeFileIdFunction<T extends (...args: never[]) => unknown> =
      T extends (...args: infer P) => infer _R
        ? P extends readonly [string, ...unknown[]]
          ? never // Reject string parameters in first position for FileId functions
          : T
        : T;

    // Mark functions that should only accept KagazFile, not regular File
    type KagazFileOnlyFunction<T extends (...args: never[]) => unknown> =
      T extends (...args: infer P) => infer _R
        ? P extends readonly [File, ...unknown[]]
          ? never // Reject File parameters in first position for KagazFile functions
          : T
        : T;

    // Utility type to enforce KagazFile usage
    type RequireKagazFile<T> = T extends File ? KagazFile : T;
  }

  // Extend Window interface for debugging
  interface Window {
    __FILE_ID_DEBUG?: boolean;
  }
}

// Augment FileContext types to prevent bypassing KagazFile
declare module "../contexts/FileContext" {
  export interface StrictFileContextActions {
    pinFile: (file: KagazFile) => void; // Must be KagazFile
    unpinFile: (file: KagazFile) => void; // Must be KagazFile
    addFiles: (
      files: File[],
      options?: { insertAfterPageId?: string },
    ) => Promise<KagazFile[]>; // Returns KagazFile
    consumeFiles: (
      inputFileIds: FileId[],
      outputFiles: File[],
    ) => Promise<KagazFile[]>; // Returns KagazFile
  }

  export interface StrictFileContextSelectors {
    getFile: (id: FileId) => KagazFile | undefined; // Returns KagazFile
    getFiles: (ids?: FileId[]) => KagazFile[]; // Returns KagazFile[]
    isFilePinned: (file: KagazFile) => boolean; // Must be KagazFile
  }
}

export {};
