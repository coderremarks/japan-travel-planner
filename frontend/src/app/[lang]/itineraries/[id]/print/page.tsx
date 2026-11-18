import { RequireAuth } from "@/components/auth/RequireAuth";
import { PrintablePlan } from "@/components/plan/PrintablePlan";

export default async function PrintPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <RequireAuth>
      <PrintablePlan id={id} />
    </RequireAuth>
  );
}
