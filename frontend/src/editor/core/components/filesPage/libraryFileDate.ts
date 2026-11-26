import type { FilesPageTab } from "@editor/contexts/FilesPageContext";
import type { KagazFileStub } from "@editor/types/fileContext";

/** Recents uses the ingestion date; an unknown date sorts before known dates in ascending order. */
export function libraryFileDate(
  file: Pick<KagazFileStub, "createdAt" | "lastModified">,
  tab?: FilesPageTab,
): number {
  return (tab === "recent" ? file.createdAt : file.lastModified) ?? 0;
}
