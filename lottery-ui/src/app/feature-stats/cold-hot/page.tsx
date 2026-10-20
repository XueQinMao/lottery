import { redirect } from "next/navigation";

export default function ColdHotPage() {
  redirect("/history-trend?feature=coldHot");
}
