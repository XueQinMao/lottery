import { redirect } from "next/navigation";

export default function FeatureTailPage() {
  redirect("/history-trend?feature=tailCounts");
}
