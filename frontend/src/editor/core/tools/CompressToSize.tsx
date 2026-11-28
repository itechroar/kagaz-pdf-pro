import { useTranslation } from "react-i18next";
import { createToolFlow } from "@editor/components/tools/shared/createToolFlow";
import CompressToSizeSettings from "@editor/components/tools/compressToSize/CompressToSizeSettings";
import { useCompressToSizeParameters } from "@editor/hooks/tools/compressToSize/useCompressToSizeParameters";
import { useCompressToSizeOperation } from "@editor/hooks/tools/compressToSize/useCompressToSizeOperation";
import { useBaseTool } from "@editor/hooks/tools/shared/useBaseTool";
import { BaseToolProps, ToolComponent } from "@editor/types/tool";

const CompressToSize = (props: BaseToolProps) => {
  const { t } = useTranslation();

  const base = useBaseTool("compressToSize", useCompressToSizeParameters, useCompressToSizeOperation, props);

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [
      {
        title: t("compressToSize.settings.title", "Size and quality"),
        isCollapsed: base.settingsCollapsed,
        onCollapsedClick: base.settingsCollapsed ? base.handleSettingsReset : undefined,
        content: (
          <CompressToSizeSettings
            parameters={base.params.parameters}
            onParameterChange={base.params.updateParameter}
            disabled={base.endpointLoading}
          />
        ),
      },
    ],
    executeButton: {
      text: t("compressToSize.submit", "Compress to size"),
      isVisible: !base.hasResults,
      loadingText: t("loading"),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t("compressToSize.title", "Compression results"),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

export default CompressToSize as ToolComponent;
