export type ReportCategory = "ibd" | "fertility" | "general" | "other";

export const CATEGORY_LABELS: Record<ReportCategory, string> = {
  ibd: "EII",
  fertility: "Fertilidad",
  general: "General",
  other: "Otro",
};

export const CATEGORY_ORDER: ReportCategory[] = ["ibd", "fertility", "general", "other"];
