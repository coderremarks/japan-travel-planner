import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Locale } from "@/i18n/config";
import { I18nProvider } from "@/i18n/I18nProvider";
import type { PlanDocument } from "@/lib/api/types";
import { PlanView } from "./PlanPanel";

const leg = {
  mode: "TRANSIT" as const,
  minutes: 25,
  costYen: 230,
  distanceMeters: 6100,
  source: "SAMPLE_TRANSIT_NETWORK" as const,
  estimated: true,
  via: [{ en: "JR Yamanote Line", ja: "JR山手線" }],
};

const plan: PlanDocument = {
  schemaVersion: 1,
  itineraryId: "trip-1",
  status: "INFEASIBLE",
  strategy: "OPTIMIZE",
  generatedAt: "2026-11-18T00:00:00Z",
  algorithmVersion: "tabiji-ils-1",
  summary: {
    scheduledCount: 1,
    mandatoryScheduled: 0,
    mandatoryTotal: 1,
    travelMinutes: 50,
    transportCostYen: 460,
    admissionYen: 1000,
    totalCostYen: 1460,
    budgetYen: 30000,
    withinBudget: true,
  },
  comparison: { baselineScheduledCount: 0, baselineTravelMinutes: 80, additionalPlaces: 1, travelMinutesSaved: 30 },
  days: [
    {
      dayIndex: 0,
      date: "2026-11-23",
      holiday: { en: "Labour Thanksgiving Day", ja: "勤労感謝の日" },
      departure: "09:00",
      returnArrival: "12:00",
      visits: [
        {
          placeId: "p1",
          slug: "tokyo-national-museum",
          name: { en: "Tokyo National Museum", ja: "東京国立博物館" },
          category: "MUSEUM",
          lat: 35.7,
          lng: 139.77,
          arrival: "09:25",
          start: "09:30",
          end: "11:30",
          waitMinutes: 5,
          mandatory: false,
          suggested: false,
          admissionYen: 1000,
          travel: leg,
        },
      ],
      returnLeg: leg,
      travelMinutes: 50,
      transportCostYen: 460,
      admissionYen: 1000,
    },
  ],
  unscheduled: [
    {
      placeId: "p2",
      slug: "kinkaku-ji",
      name: { en: "Kinkaku-ji", ja: "金閣寺" },
      mandatory: true,
      suggested: false,
      reason: "NO_ROUTE",
    },
  ],
  hints: ["ALLOW_MORE_TRAVEL_MODES"],
  notices: ["ESTIMATED_TRAVEL", "SAMPLE_CATALOG_DATA"],
  solver: {
    iterations: 160,
    improvingMoves: 12,
    elapsedMillis: 42,
    seed: 1,
    timeLimitReached: false,
    constructionScheduled: 1,
    constructionTravelMinutes: 60,
  },
};

function renderPlan(locale: Locale, stale = false) {
  return render(
    <I18nProvider locale={locale}>
      <PlanView document={plan} stale={stale} itineraryId="trip-1" baseName={{ en: "Ueno", ja: "上野" }} />
    </I18nProvider>,
  );
}

describe("PlanView", () => {
  it("explains the result in English", () => {
    renderPlan("en");
    expect(screen.getByText("Not all must-visits could be scheduled")).toBeInTheDocument();
    expect(screen.getByText(/No route with the travel modes you allowed/)).toBeInTheDocument();
    expect(screen.getByText(/Allow taxis/)).toBeInTheDocument();
    expect(screen.getByText(/1 more place and 30 min less travel/)).toBeInTheDocument();
    expect(screen.getByText("National holiday: Labour Thanksgiving Day")).toBeInTheDocument();
    expect(screen.getByText(/Travel times and fares are estimates/)).toBeInTheDocument();
    expect(screen.getAllByText("estimate").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/via JR Yamanote Line/).length).toBeGreaterThan(0);
  });

  it("renders the same plan in Japanese", () => {
    renderPlan("ja");
    expect(screen.getByText("「必ず行く」場所の一部が入りませんでした")).toBeInTheDocument();
    expect(screen.getByText("東京国立博物館")).toBeInTheDocument();
    expect(screen.getByText("祝日：勤労感謝の日")).toBeInTheDocument();
    expect(screen.getByText(/選択した移動手段では行けません/)).toBeInTheDocument();
    expect(screen.getByText(/09:25 到着、開館まで 5分 待ち/)).toBeInTheDocument();
  });

  it("warns when the plan is out of date and offers exports", () => {
    renderPlan("en", true);
    expect(screen.getByText(/Your trip changed since this plan was made/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Calendar/ })).toHaveAttribute(
      "href",
      "/api/v1/itineraries/trip-1/calendar.ics?lang=en",
    );
  });

  it("escapes place names instead of rendering markup", () => {
    const hostile: PlanDocument = {
      ...plan,
      unscheduled: [{ ...plan.unscheduled[0], name: { en: "<img src=x onerror=alert(1)>", ja: "x" } }],
    };
    const { container } = render(
      <I18nProvider locale="en">
        <PlanView document={hostile} stale={false} itineraryId="t" baseName={{ en: "Ueno", ja: "上野" }} />
      </I18nProvider>,
    );
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
  });
});
