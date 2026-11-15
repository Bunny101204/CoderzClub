import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "../apiClient";
import { useAuth } from "./AuthContext";
import {
  clearStoredOrganizationId,
  notifyOrganizationChanged,
  organizationCacheEpoch,
  persistOrganizationId,
  readStoredOrganizationId,
  resolveActiveOrganizationId,
} from "../organization/activeOrganization.js";
import { resolveOrganizationBranding, brandingCssVariables, DEFAULT_BRANDING } from "../organization/branding.js";
import { canAdministerOrganization, canManageInstitutionBatches } from "../organization/organizationRoles.js";

const OrganizationContext = createContext({
  organizations: [],
  activeOrganization: null,
  activeOrganizationId: null,
  branding: DEFAULT_BRANDING,
  loading: false,
  cacheEpoch: organizationCacheEpoch(null, 0),
  canAdministerActive: false,
  canManageBatches: false,
  switchOrganization: async () => {},
  refreshOrganizations: async () => [],
});

export function OrganizationProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [organizations, setOrganizations] = useState([]);
  const [activeOrganizationId, setActiveOrganizationId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [generation, setGeneration] = useState(0);

  const refreshOrganizations = useCallback(async () => {
    if (!isAuthenticated) {
      setOrganizations([]);
      setActiveOrganizationId(null);
      clearStoredOrganizationId();
      return [];
    }
    setLoading(true);
    try {
      const response = await api.organizations.listMine();
      const list = Array.isArray(response?.data?.organizations) ? response.data.organizations : [];
      setOrganizations(list);
      const preferred = readStoredOrganizationId();
      const nextId = resolveActiveOrganizationId(list, preferred);
      setActiveOrganizationId(nextId);
      persistOrganizationId(nextId);
      return list;
    } catch {
      setOrganizations([]);
      setActiveOrganizationId(null);
      return [];
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    refreshOrganizations();
  }, [refreshOrganizations]);

  const switchOrganization = useCallback(async (organizationId) => {
    const nextId = resolveActiveOrganizationId(organizations, organizationId);
    if (!nextId || nextId === activeOrganizationId) {
      return nextId;
    }
    setActiveOrganizationId(nextId);
    persistOrganizationId(nextId);
    setGeneration((value) => value + 1);
    notifyOrganizationChanged({ organizationId: nextId });
    try {
      await api.organizations.activeContext(nextId);
    } catch {
      // Membership revocation is handled on subsequent API calls.
    }
    return nextId;
  }, [organizations, activeOrganizationId]);

  const activeOrganization = useMemo(
    () => organizations.find((org) => org.id === activeOrganizationId) || null,
    [organizations, activeOrganizationId]
  );

  const branding = useMemo(
    () => (activeOrganization ? resolveOrganizationBranding(activeOrganization) : DEFAULT_BRANDING),
    [activeOrganization]
  );

  useEffect(() => {
    if (typeof document === "undefined") {
      return undefined;
    }
    const root = document.documentElement;
    const vars = brandingCssVariables(branding);
    Object.entries(vars).forEach(([key, value]) => root.style.setProperty(key, value));
    const previousTitle = document.title;
    if (branding.source === "organization") {
      document.title = `${branding.displayName} · CoderzClub`;
    }
    let favicon = document.querySelector("link[data-org-favicon='true']");
    if (branding.faviconUrl) {
      if (!favicon) {
        favicon = document.createElement("link");
        favicon.rel = "icon";
        favicon.setAttribute("data-org-favicon", "true");
        document.head.appendChild(favicon);
      }
      favicon.href = branding.faviconUrl;
    } else if (favicon) {
      favicon.remove();
    }
    return () => {
      Object.keys(vars).forEach((key) => root.style.removeProperty(key));
      document.title = previousTitle;
    };
  }, [branding]);

  const value = useMemo(() => ({
    organizations,
    activeOrganization,
    activeOrganizationId,
    branding,
    loading,
    cacheEpoch: organizationCacheEpoch(activeOrganizationId, generation),
    canAdministerActive: canAdministerOrganization(activeOrganization?.viewerRoles),
    canManageBatches: canManageInstitutionBatches(activeOrganization?.viewerRoles),
    switchOrganization,
    refreshOrganizations,
  }), [
    organizations,
    activeOrganization,
    activeOrganizationId,
    branding,
    loading,
    generation,
    switchOrganization,
    refreshOrganizations,
  ]);

  return (
    <OrganizationContext.Provider value={value}>
      {children}
    </OrganizationContext.Provider>
  );
}

export function useOrganization() {
  return useContext(OrganizationContext);
}
