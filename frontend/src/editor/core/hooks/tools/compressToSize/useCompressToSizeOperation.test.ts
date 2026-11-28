import { describe, expect, test } from "vitest";
import {
  compressToSizeFromApiParams,
  compressToSizeToApiParams,
} from "@editor/hooks/tools/compressToSize/useCompressToSizeOperation";
import {
  defaultParameters,
  validateCompressToSizeParameters,
} from "@editor/hooks/tools/compressToSize/useCompressToSizeParameters";

describe("compress to size parameters", () => {
  test("maps the target and strength onto the compress request", () => {
    const api = compressToSizeToApiParams({
      ...defaultParameters,
      sizeValue: "1.5",
      sizeUnit: "MB",
      strength: "extreme",
      grayscale: true,
    });

    expect(api.expectedOutputSize).toBe("1.5MB");
    expect(api.minOptimizeLevel).toBe(9);
    expect(api.grayscale).toBe(true);
  });

  test("less compression leaves the target in charge", () => {
    expect(compressToSizeToApiParams(defaultParameters).minOptimizeLevel).toBe(1);
  });

  test("round-trips through the API shape", () => {
    const params = {
      ...defaultParameters,
      sizeValue: "200",
      sizeUnit: "KB" as const,
      strength: "more" as const,
    };

    expect(compressToSizeFromApiParams(compressToSizeToApiParams(params))).toMatchObject({
      sizeValue: "200",
      sizeUnit: "KB",
      strength: "more",
    });
  });

  test("a size is required and must be positive", () => {
    expect(validateCompressToSizeParameters(defaultParameters)).toBe(true);
    expect(validateCompressToSizeParameters({ ...defaultParameters, sizeValue: "" })).toBe(false);
    expect(validateCompressToSizeParameters({ ...defaultParameters, sizeValue: "0" })).toBe(false);
  });
});
