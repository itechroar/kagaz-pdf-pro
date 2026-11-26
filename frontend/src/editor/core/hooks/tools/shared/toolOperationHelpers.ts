import {
  KagazFile,
  FileId,
  KagazFileStub,
  createKagazFile,
  ProcessedFileMetadata,
  createNewKagazFileStub,
} from "@editor/types/fileContext";

/**
 * Builds parallel inputFileIds and inputKagazFileStubs arrays from the valid input files.
 * Falls back to a fresh stub when the file is not found in the current context state
 * (e.g. it was removed between operation start and this point).
 */
export function buildInputTracking(
  validFiles: KagazFile[],
  selectors: {
    getKagazFileStub: (id: FileId) => KagazFileStub | undefined;
  },
): { inputFileIds: FileId[]; inputKagazFileStubs: KagazFileStub[] } {
  const inputFileIds: FileId[] = [];
  const inputKagazFileStubs: KagazFileStub[] = [];
  for (const file of validFiles) {
    const fileId = file.fileId;
    const record = selectors.getKagazFileStub(fileId);
    if (record) {
      inputFileIds.push(fileId);
      inputKagazFileStubs.push(record);
    } else {
      console.debug(`No file stub found for file: ${file.name}`);
      inputFileIds.push(fileId);
      inputKagazFileStubs.push(createNewKagazFileStub(file, fileId));
    }
  }
  return { inputFileIds, inputKagazFileStubs };
}

/**
 * Creates parallel outputKagazFileStubs and outputKagazFiles arrays from processed files.
 * The stubFactory determines how each stub is constructed (child version vs fresh root).
 */
export function buildOutputPairs(
  processedFiles: File[],
  thumbnails: string[],
  metadataArray: Array<ProcessedFileMetadata | undefined>,
  stubFactory: (
    file: File,
    thumbnail: string,
    metadata: ProcessedFileMetadata | undefined,
    index: number,
  ) => KagazFileStub,
): {
  outputKagazFileStubs: KagazFileStub[];
  outputKagazFiles: KagazFile[];
} {
  const outputKagazFileStubs = processedFiles.map((file, index) =>
    stubFactory(file, thumbnails[index], metadataArray[index], index),
  );
  const outputKagazFiles = processedFiles.map((file, index) =>
    createKagazFile(file, outputKagazFileStubs[index].id),
  );
  return { outputKagazFileStubs, outputKagazFiles };
}
