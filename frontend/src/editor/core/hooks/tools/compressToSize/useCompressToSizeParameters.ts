import { BaseParameters } from "@editor/types/parameters";
import { useBaseParameters, BaseParametersHook } from "@editor/hooks/tools/shared/useBaseParameters";

export type CompressStrength = "less" | "balanced" | "more" | "extreme";

export interface CompressToSizeParameters extends BaseParameters {
  sizeValue: string;
  sizeUnit: "KB" | "MB";
  strength: CompressStrength;
  grayscale: boolean;
}

/** Lowest backend optimisation level each strength allows; "less" lets the target alone decide. */
export const STRENGTH_MIN_LEVEL: Record<CompressStrength, 1 | 4 | 6 | 9> = {
  less: 1,
  balanced: 4,
  more: 6,
  extreme: 9,
};

export const SIZE_PRESETS: ReadonlyArray<{
  value: string;
  unit: "KB" | "MB";
}> = [
  { value: "100", unit: "KB" },
  { value: "200", unit: "KB" },
  { value: "500", unit: "KB" },
  { value: "1", unit: "MB" },
  { value: "2", unit: "MB" },
  { value: "5", unit: "MB" },
];

export const defaultParameters: CompressToSizeParameters = {
  sizeValue: "500",
  sizeUnit: "KB",
  strength: "less",
  grayscale: false,
};

export function validateCompressToSizeParameters(params: CompressToSizeParameters): boolean {
  const size = Number(params.sizeValue);
  return Number.isFinite(size) && size > 0;
}

export type CompressToSizeParametersHook = BaseParametersHook<CompressToSizeParameters>;

export const useCompressToSizeParameters = (): CompressToSizeParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: "compress-pdf",
    validateFn: validateCompressToSizeParameters,
  });
};
