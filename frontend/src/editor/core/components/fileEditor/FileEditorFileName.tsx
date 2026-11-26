import React from "react";
import { KagazFileStub } from "@editor/types/fileContext";
import { PrivateContent } from "@editor/components/shared/PrivateContent";
import { truncateCenter } from "@editor/utils/textUtils";

interface FileEditorFileNameProps {
  file: KagazFileStub;
  maxLength?: number;
}

const FileEditorFileName = ({
  file,
  maxLength = 40,
}: FileEditorFileNameProps) => (
  <PrivateContent>{truncateCenter(file.name, maxLength)}</PrivateContent>
);

export default FileEditorFileName;
