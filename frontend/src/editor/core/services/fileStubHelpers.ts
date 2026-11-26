import { KagazFile, KagazFileStub } from "@editor/types/fileContext";
import {
  createChildStub,
  generateProcessedFileMetadata,
} from "@editor/contexts/file/fileActions";
import { createKagazFile } from "@editor/types/fileContext";
import { ToolId } from "@editor/types/toolId";

/**
 * Create KagazFiles and KagazFileStubs from exported files
 * Used when saving page editor changes to create version history
 */
export async function createKagazFilesAndStubs(
  files: File[],
  parentStub: KagazFileStub,
  toolId: ToolId,
  /** Shown instead of the tool's name in version history (a policy passes its pipeline name). */
  label?: string,
): Promise<{ kagazFiles: KagazFile[]; stubs: KagazFileStub[] }> {
  const kagazFiles: KagazFile[] = [];
  const stubs: KagazFileStub[] = [];

  for (const file of files) {
    const processedFileMetadata = await generateProcessedFileMetadata(file);
    const childStub = createChildStub(
      parentStub,
      { toolId, timestamp: Date.now(), ...(label ? { label } : {}) },
      file,
      processedFileMetadata?.thumbnailUrl,
      processedFileMetadata,
    );

    const kagazFile = createKagazFile(file, childStub.id);
    kagazFiles.push(kagazFile);
    stubs.push(childStub);
  }

  return { kagazFiles, stubs };
}
