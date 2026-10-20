import { redirect } from "next/navigation";

export default function FeatureStatsPage() {
  redirect("/history-trend?feature=sum");
}
