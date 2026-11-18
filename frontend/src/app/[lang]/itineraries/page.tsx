import { RequireAuth } from "@/components/auth/RequireAuth";
import { TripList } from "@/components/trips/TripList";

export default function ItinerariesPage() {
  return (
    <RequireAuth>
      <TripList />
    </RequireAuth>
  );
}
