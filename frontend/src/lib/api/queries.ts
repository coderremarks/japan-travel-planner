"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, CSRF_COOKIE, CSRF_HEADER, api, readCookie, unwrap } from "./client";
import type { Category, Itinerary, ItineraryRequest, PlanResponse, PlanStrategy, User } from "./types";

/** Query keys in one place so invalidation stays consistent. */
export const keys = {
  me: ["me"] as const,
  areas: ["areas"] as const,
  baseLocations: ["base-locations"] as const,
  places: (filters: PlaceFilters) => ["places", filters] as const,
  itineraries: (page: number) => ["itineraries", page] as const,
  itinerary: (id: string) => ["itinerary", id] as const,
  plan: (id: string) => ["plan", id] as const,
};

export type PlaceFilters = { area?: string; category?: Category; q?: string };

/** The signed-in user, or `null` when not signed in (401 is an answer here, not an error). */
export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: async (): Promise<User | null> => {
      const result = await api.GET("/api/v1/auth/me");
      if (result.response.status === 401) {
        return null;
      }
      return unwrap(result);
    },
    staleTime: 60_000,
  });
}

export function useLogin() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (body: { email: string; password: string }) =>
      unwrap(await api.POST("/api/v1/auth/login", { body })),
    onSuccess: (user) => {
      client.clear();
      client.setQueryData(keys.me, user);
    },
  });
}

export function useRegister() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (body: { email: string; password: string; displayName: string; preferredLocale: "en" | "ja" }) =>
      unwrap(await api.POST("/api/v1/auth/register", { body })),
    onSuccess: (user) => {
      client.clear();
      client.setQueryData(keys.me, user);
    },
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const response = await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: await csrfHeaders(),
      });
      if (!response.ok) {
        throw new ApiError(response.status, "generic");
      }
    },
    onSuccess: () => {
      client.clear();
      client.setQueryData(keys.me, null);
    },
  });
}

/** Logout is handled by Spring Security's filter (not in the OpenAPI document), so it is called directly. */
async function csrfHeaders(): Promise<HeadersInit> {
  let token = readCookie(CSRF_COOKIE);
  if (!token) {
    await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
    token = readCookie(CSRF_COOKIE);
  }
  return token ? { [CSRF_HEADER]: token } : {};
}

export function useAreas() {
  return useQuery({
    queryKey: keys.areas,
    queryFn: async () => unwrap(await api.GET("/api/v1/areas")),
    staleTime: Infinity,
  });
}

export function useBaseLocations() {
  return useQuery({
    queryKey: keys.baseLocations,
    queryFn: async () => unwrap(await api.GET("/api/v1/base-locations")),
    staleTime: Infinity,
  });
}

export function usePlaces(filters: PlaceFilters) {
  return useQuery({
    queryKey: keys.places(filters),
    queryFn: async () =>
      unwrap(
        await api.GET("/api/v1/places", {
          params: {
            query: {
              area: filters.area || undefined,
              category: filters.category || undefined,
              q: filters.q?.trim() || undefined,
              size: 20,
            },
          },
        }),
      ),
    placeholderData: keepPreviousData,
    staleTime: 5 * 60_000,
  });
}

export function useItineraries(page = 0) {
  return useQuery({
    queryKey: keys.itineraries(page),
    queryFn: async () => unwrap(await api.GET("/api/v1/itineraries", { params: { query: { page, size: 50 } } })),
  });
}

export function useItinerary(id: string) {
  return useQuery({
    queryKey: keys.itinerary(id),
    queryFn: async () => unwrap(await api.GET("/api/v1/itineraries/{id}", { params: { path: { id } } })),
    retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
  });
}

/** Latest plan, or `null` if none has been generated yet. */
export function usePlan(id: string) {
  return useQuery({
    queryKey: keys.plan(id),
    queryFn: async (): Promise<PlanResponse | null> => {
      const result = await api.GET("/api/v1/itineraries/{id}/plan", { params: { path: { id } } });
      if (result.response.status === 404) {
        return null;
      }
      return unwrap(result);
    },
  });
}

export function useCreateItinerary() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (body: ItineraryRequest) => unwrap(await api.POST("/api/v1/itineraries", { body })),
    onSuccess: (itinerary) => {
      client.setQueryData(keys.itinerary(itinerary.id), itinerary);
      void client.invalidateQueries({ queryKey: ["itineraries"] });
    },
  });
}

/** Saves with `If-Match` so a stale tab gets 412 instead of silently overwriting newer edits. */
export function useUpdateItinerary(id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async ({ body, version }: { body: ItineraryRequest; version: number }) =>
      unwrap(
        await api.PUT("/api/v1/itineraries/{id}", {
          params: { path: { id }, header: { "If-Match": `"${version}"` } },
          body,
        }),
      ),
    onSuccess: (itinerary: Itinerary) => {
      client.setQueryData(keys.itinerary(id), itinerary);
      void client.invalidateQueries({ queryKey: keys.plan(id) });
      void client.invalidateQueries({ queryKey: ["itineraries"] });
    },
  });
}

export function useDeleteItinerary() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) =>
      unwrap(await api.DELETE("/api/v1/itineraries/{id}", { params: { path: { id } } })),
    onSuccess: () => void client.invalidateQueries({ queryKey: ["itineraries"] }),
  });
}

export function useDuplicateItinerary() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) =>
      unwrap(await api.POST("/api/v1/itineraries/{id}/duplicate", { params: { path: { id } } })),
    onSuccess: () => void client.invalidateQueries({ queryKey: ["itineraries"] }),
  });
}

export function useGeneratePlan(id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (strategy: PlanStrategy) =>
      unwrap(
        await api.POST("/api/v1/itineraries/{id}/plan", {
          params: { path: { id } },
          body: { strategy },
        }),
      ),
    onSuccess: (plan) => {
      client.setQueryData(keys.plan(id), plan);
      void client.invalidateQueries({ queryKey: keys.itinerary(id) });
      void client.invalidateQueries({ queryKey: ["itineraries"] });
    },
  });
}
