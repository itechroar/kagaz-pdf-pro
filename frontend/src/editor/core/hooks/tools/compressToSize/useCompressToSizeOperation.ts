import { useTranslation } from "react-i18next";
import { useToolOperation, defineSingleFileTool } from "@editor/hooks/tools/shared/useToolOperation";
import { objectToFormData, type ToolApiParams, type ToolEndpoint } from "@editor/hooks/tools/shared/toolApiMapping";
import { createStandardErrorHandler } from "@editor/utils/toolErrorHandler";
import {
  validateCompressToSizeParameters,
  CompressToSizeParameters,
  CompressStrength,
  STRENGTH_MIN_LEVEL,
  defaultParameters,
} from "@editor/hooks/tools/compressToSize/useCompressToSizeParameters";

const ENDPOINT = "/api/v1/misc/compress-pdf" satisfies ToolEndpoint;
type CompressApiParams = ToolApiParams[typeof ENDPOINT];

export const compressToSizeToApiParams = (parameters: CompressToSizeParameters): CompressApiParams => ({
  optimizeLevel: 5,
  expectedOutputSize: `${parameters.sizeValue}${parameters.sizeUnit}`,
  minOptimizeLevel: STRENGTH_MIN_LEVEL[parameters.strength],
  grayscale: parameters.grayscale,
});

function strengthForLevel(level: number | undefined): CompressStrength {
  if (level === undefined || level <= 3) return "less";
  if (level <= 5) return "balanced";
  if (level <= 8) return "more";
  return "extreme";
}

export const compressToSizeFromApiParams = (apiParams: CompressApiParams): Partial<CompressToSizeParameters> => {
  const result: Partial<CompressToSizeParameters> = {
    strength: strengthForLevel(apiParams.minOptimizeLevel),
    grayscale: apiParams.grayscale ?? defaultParameters.grayscale,
  };
  const match = /^(\d+(?:\.\d+)?)(KB|MB)$/i.exec(apiParams.expectedOutputSize ?? "");
  if (match) {
    result.sizeValue = match[1];
    result.sizeUnit = match[2].toUpperCase() as "KB" | "MB";
  }
  return result;
};

export const buildCompressToSizeFormData = (parameters: CompressToSizeParameters, file: File): FormData =>
  objectToFormData(compressToSizeToApiParams(parameters), { fileInput: file });

export const compressToSizeOperationConfig = defineSingleFileTool({
  validateParams: validateCompressToSizeParameters,
  buildFormData: buildCompressToSizeFormData,
  toApiParams: compressToSizeToApiParams,
  fromApiParams: compressToSizeFromApiParams,
  operationType: "compress",
  endpoint: ENDPOINT,
  defaultParameters,
});

export const useCompressToSizeOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<CompressToSizeParameters>({
    ...compressToSizeOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t("compressToSize.error.failed", "An error occurred while compressing the PDF."),
    ),
  });
};
