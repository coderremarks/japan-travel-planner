import { RequireAuth } from "@/components/auth/RequireAuth";
import { ItineraryWorkspace } from "@/components/itinerary/ItineraryWorkspace";

export default async function ItineraryPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <RequireAuth>
      <ItineraryWorkspace id={id} />
    </RequireAuth>
  );
}
