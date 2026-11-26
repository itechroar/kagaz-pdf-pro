import { useAppConfig } from "@editor/contexts/AppConfigContext";

export const useBaseUrl = (): string => {
  const { config } = useAppConfig();
  return config?.baseUrl || "https://github.com/itechroar/kagaz-pdf";
};
