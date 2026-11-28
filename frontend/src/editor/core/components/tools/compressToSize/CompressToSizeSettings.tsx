import { Stack, Text, NumberInput, Checkbox, Radio, Group } from "@mantine/core";
import { Button } from "@editor/ui/Button";
import { SegmentedControl } from "@editor/ui/SegmentedControl";
import { useTranslation } from "react-i18next";
import {
  CompressStrength,
  CompressToSizeParameters,
  SIZE_PRESETS,
} from "@editor/hooks/tools/compressToSize/useCompressToSizeParameters";

interface CompressToSizeSettingsProps {
  parameters: CompressToSizeParameters;
  onParameterChange: <K extends keyof CompressToSizeParameters>(key: K, value: CompressToSizeParameters[K]) => void;
  disabled?: boolean;
}

const CompressToSizeSettings = ({ parameters, onParameterChange, disabled = false }: CompressToSizeSettingsProps) => {
  const { t } = useTranslation();

  const strengths: Array<{
    value: CompressStrength;
    label: string;
    description: string;
  }> = [
    {
      value: "less",
      label: t("compressToSize.strength.less.label", "Less compression"),
      description: t(
        "compressToSize.strength.less.desc",
        "Best quality. Compresses only as much as needed to reach the size.",
      ),
    },
    {
      value: "balanced",
      label: t("compressToSize.strength.balanced.label", "Balanced"),
      description: t("compressToSize.strength.balanced.desc", "Good quality with a noticeably smaller file."),
    },
    {
      value: "more",
      label: t("compressToSize.strength.more.label", "More compression"),
      description: t("compressToSize.strength.more.desc", "Smaller file, lower image quality."),
    },
    {
      value: "extreme",
      label: t("compressToSize.strength.extreme.label", "Extreme compression"),
      description: t(
        "compressToSize.strength.extreme.desc",
        "Smallest possible file, lowest quality. Text stays sharp; images may look soft.",
      ),
    },
  ];

  return (
    <Stack gap="lg">
      <Stack gap="xs">
        <Text size="sm" fw={600}>
          {t("compressToSize.target.title", "Target file size")}
        </Text>
        <Group gap="xs" align="flex-end" wrap="nowrap">
          <NumberInput
            aria-label={t("compressToSize.target.value", "Size")}
            value={parameters.sizeValue}
            onChange={(value) => onParameterChange("sizeValue", value?.toString() ?? "")}
            min={0}
            decimalScale={2}
            allowNegative={false}
            disabled={disabled}
            style={{ flex: 1 }}
          />
          <SegmentedControl
            value={parameters.sizeUnit}
            onChange={(value) => onParameterChange("sizeUnit", value as "KB" | "MB")}
            options={[
              { value: "KB", label: "KB" },
              { value: "MB", label: "MB" },
            ]}
            disabled={disabled}
          />
        </Group>
        <Group gap={6}>
          {SIZE_PRESETS.map((preset) => {
            const active = parameters.sizeValue === preset.value && parameters.sizeUnit === preset.unit;
            return (
              <Button
                key={`${preset.value}${preset.unit}`}
                size="sm"
                variant={active ? "primary" : "secondary"}
                disabled={disabled}
                onClick={() => {
                  onParameterChange("sizeValue", preset.value);
                  onParameterChange("sizeUnit", preset.unit);
                }}
              >
                {preset.value} {preset.unit}
              </Button>
            );
          })}
        </Group>
        <Text size="xs" c="dimmed">
          {t(
            "compressToSize.target.hint",
            "Each file is compressed toward this size. Files with little to shrink, or targets far below what the content allows, may end up larger than requested.",
          )}
        </Text>
      </Stack>

      <Radio.Group
        label={t("compressToSize.strength.title", "Compression strength")}
        value={parameters.strength}
        onChange={(value) => onParameterChange("strength", value as CompressStrength)}
      >
        <Stack gap="xs" mt="xs">
          {strengths.map((strength) => (
            <Radio
              key={strength.value}
              value={strength.value}
              label={strength.label}
              description={strength.description}
              disabled={disabled}
            />
          ))}
        </Stack>
      </Radio.Group>

      <Checkbox
        checked={parameters.grayscale}
        onChange={(event) => onParameterChange("grayscale", event.currentTarget.checked)}
        disabled={disabled}
        label={t("compressToSize.grayscale.label", "Convert to grayscale")}
        description={t("compressToSize.grayscale.desc", "Drops colour to save more space on scans and photos.")}
      />
    </Stack>
  );
};

export default CompressToSizeSettings;
