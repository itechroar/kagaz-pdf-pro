import { type TFunction } from "i18next";
import { Icon } from "@editor/ui/Icon";
import React from "react";
import {
  type ErasedToolParams,
  type ToolAutomationSettingsProps,
  ToolOperationConfig,
} from "@editor/hooks/tools/shared/toolOperationTypes";
import { BaseToolProps } from "@editor/types/tool";
import { WorkbenchType } from "@editor/types/workbench";
import {
  LinkToolId,
  RegularToolId,
  SuperToolId,
  ToolId,
  ToolKind,
} from "@editor/types/toolId";
import { ProprietaryToolId } from "@editor/types/proprietaryToolId";

export enum SubcategoryId {
  FREQUENTLY_USED = "frequentlyUsed",
  EDIT = "edit",
  ORGANIZE = "organize",
  OPTIMIZE_REPAIR = "optimizeRepair",
  SECURITY_PRIVACY = "securityPrivacy",
  VIEW_CHECK = "viewCheck",
  CONVERT = "convert",
  AI = "ai",
  AUTOMATION = "automation",
  DEVELOPER_TOOLS = "developerTools",
}

export enum ToolCategoryId {
  STANDARD_TOOLS = "standardTools",
  ADVANCED_TOOLS = "advancedTools",
  RECOMMENDED_TOOLS = "recommendedTools",
}

export type ToolRegistryEntry = {
  icon: React.ReactNode;
  name: string;
  component: React.ComponentType<BaseToolProps> | null;
  /** Fetches `component`'s code ahead of first use, for tools opened in place where a load delay shows. */
  preload?: () => Promise<unknown>;
  description: string;
  categoryId: ToolCategoryId;
  subcategoryId: SubcategoryId;
  /** Second home in the full tool list for a tool whose main listing is Frequently used. */
  alsoListedIn?: SubcategoryId;
  maxFiles?: number;
  supportedFormats?: string[];
  endpoints?: string[];
  link?: string;
  kind?: ToolKind;
  // Workbench type for navigation
  workbench?: WorkbenchType;
  // Operation configuration for automation. TParams is erased at the registry
  // boundary; tools are authored type-safely via defineToolAutomation.
  operationConfig?: ToolOperationConfig<ErasedToolParams>;
  // Settings component for automation configuration.
  automationSettings: React.ComponentType<
    ToolAutomationSettingsProps<ErasedToolParams>
  > | null;
  // Whether this tool supports automation (defaults to true)
  supportsAutomate?: boolean;
  // Keep out of the editor's tool list: a step only a pipeline runs, with no UI to open.
  hiddenFromToolList?: boolean;
  // Synonyms for search (optional)
  synonyms?: string[];
  // Version status indicator (e.g., "alpha", "beta")
  versionStatus?: "alpha" | "beta";
  // Whether this tool requires premium access
  requiresPremium?: boolean;
  // Pre-translated override for the generic "unavailable on server" tooltip label
  unavailableMessage?: string;
};

export type RegularToolRegistry = Record<RegularToolId, ToolRegistryEntry>;
export type SuperToolRegistry = Record<SuperToolId, ToolRegistryEntry>;
export type LinkToolRegistry = Record<LinkToolId, ToolRegistryEntry>;
export type ToolRegistry = Record<ToolId, ToolRegistryEntry>;
export type ProprietaryToolRegistry = Record<
  ProprietaryToolId,
  ToolRegistryEntry
>;

export const SUBCATEGORY_ORDER: SubcategoryId[] = [
  SubcategoryId.FREQUENTLY_USED,
  SubcategoryId.EDIT,
  SubcategoryId.ORGANIZE,
  SubcategoryId.OPTIMIZE_REPAIR,
  SubcategoryId.SECURITY_PRIVACY,
  SubcategoryId.VIEW_CHECK,
  SubcategoryId.CONVERT,
  SubcategoryId.AI,
  SubcategoryId.AUTOMATION,
  SubcategoryId.DEVELOPER_TOOLS,
];

export const SUBCATEGORY_COLOR_MAP: Record<SubcategoryId, string> = {
  [SubcategoryId.FREQUENTLY_USED]: "var(--category-color-general)", // Blue
  [SubcategoryId.EDIT]: "var(--category-color-formatting)", // Purple
  [SubcategoryId.ORGANIZE]: "var(--category-color-extraction)", // Cyan
  [SubcategoryId.OPTIMIZE_REPAIR]: "var(--category-color-removal)", // Red
  [SubcategoryId.SECURITY_PRIVACY]: "var(--category-color-security)", // Orange
  [SubcategoryId.VIEW_CHECK]: "var(--category-color-verification)", // Orange
  [SubcategoryId.CONVERT]: "var(--category-color-signing)", // Green
  [SubcategoryId.AI]: "var(--category-color-automation)", // Pink
  [SubcategoryId.AUTOMATION]: "var(--category-color-automation)", // Pink
  [SubcategoryId.DEVELOPER_TOOLS]: "var(--category-color-developer)", // Gray
};

export const getSubcategoryIcon = (
  subcategory: SubcategoryId,
): React.ReactNode => {
  switch (subcategory) {
    case SubcategoryId.FREQUENTLY_USED:
      return React.createElement(Icon, { name: "star" });
    case SubcategoryId.EDIT:
      return React.createElement(Icon, { name: "pen-tool" });
    case SubcategoryId.ORGANIZE:
      return React.createElement(Icon, { name: "rows-3" });
    case SubcategoryId.OPTIMIZE_REPAIR:
      return React.createElement(Icon, { name: "wrench" });
    // Siblings in the same picker list, so they must not share a glyph:
    // security is the bare shield, view & check the one with the tick.
    case SubcategoryId.SECURITY_PRIVACY:
      return React.createElement(Icon, { name: "shield" });
    case SubcategoryId.VIEW_CHECK:
      return React.createElement(Icon, { name: "shield-check" });
    case SubcategoryId.CONVERT:
      return React.createElement(Icon, { name: "file-convert" });
    case SubcategoryId.AUTOMATION:
      return React.createElement(Icon, { name: "bot" });
    case SubcategoryId.DEVELOPER_TOOLS:
      return React.createElement(Icon, { name: "code" });
    default:
      return React.createElement(Icon, { name: "wrench" });
  }
};

export const getSubcategoryLabel = (t: TFunction, id: SubcategoryId): string =>
  t(`toolPicker.subcategories.${id}`, id);
export const getSubcategoryColor = (subcategory: SubcategoryId): string =>
  SUBCATEGORY_COLOR_MAP[subcategory] || "#7882FF";

export const getAllEndpoints = (registry: ToolRegistry): string[] => {
  const lists: string[][] = [];
  Object.values(registry).forEach((entry) => {
    if (entry.endpoints && entry.endpoints.length > 0) {
      lists.push(entry.endpoints);
    }
  });
  return Array.from(new Set(lists.flat()));
};

export const getConversionEndpoints = (
  extensionToEndpoint: Record<string, Record<string, string>>,
): string[] => {
  const endpoints = new Set<string>();
  Object.values(extensionToEndpoint).forEach((toEndpoints) => {
    Object.values(toEndpoints).forEach((endpoint) => {
      endpoints.add(endpoint);
    });
  });
  return Array.from(endpoints);
};

export const getAllApplicationEndpoints = (
  registry: ToolRegistry,
  extensionToEndpoint?: Record<string, Record<string, string>>,
): string[] => {
  const toolEp = getAllEndpoints(registry);
  const convEp = extensionToEndpoint
    ? getConversionEndpoints(extensionToEndpoint)
    : [];
  return Array.from(new Set([...toolEp, ...convEp]));
};

/**
 * Default workbench for tools that don't specify one
 * Returns null to trigger the default case in Workbench component (ToolRenderer)
 */
export const getDefaultToolWorkbench = (): WorkbenchType => "fileEditor";

/**
 * Get workbench type for a tool
 */
export const getToolWorkbench = (tool: ToolRegistryEntry): WorkbenchType => {
  return tool.workbench || getDefaultToolWorkbench();
};

/**
 * Get URL path for a tool
 */
export const getToolUrlPath = (toolId: string): string => {
  return `/${toolId.replace(/([A-Z])/g, "-$1").toLowerCase()}`;
};

/**
 * Check if a tool ID exists in the registry
 */
export const isValidToolId = (
  toolId: string,
  registry: ToolRegistry,
): boolean => {
  return toolId in registry;
};

/**
 * A "coming soon" placeholder: listed in the catalogue but not openable — no
 * UI component and no external link. "read" and "multiTool" are exempt as
 * workbench-only tools that render without a component.
 */
export const isComingSoonTool = (
  toolId: string,
  tool: ToolRegistryEntry,
): boolean =>
  !tool.component && !tool.link && toolId !== "read" && toolId !== "multiTool";

/**
 * Check if a tool supports automation (defaults to true)
 */
export const getToolSupportsAutomate = (tool: ToolRegistryEntry): boolean => {
  return tool.supportsAutomate !== false;
};
