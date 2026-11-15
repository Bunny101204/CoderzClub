import React from "react";
import { useOrganization } from "../../context/OrganizationContext";

export default function OrganizationSwitcher({ className = "" }) {
  const {
    organizations,
    activeOrganizationId,
    activeOrganization,
    switchOrganization,
    loading,
  } = useOrganization();

  if (!organizations.length) {
    return null;
  }

  const label = activeOrganization?.branding?.displayName
    || activeOrganization?.name
    || "Institution";

  return (
    <label className={`inline-flex items-center gap-2 min-w-0 ${className}`}>
      <span className="sr-only">Active institution</span>
      <select
        aria-label="Switch institution"
        data-testid="organization-switcher"
        className="max-w-[10rem] sm:max-w-[14rem] truncate rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
        value={activeOrganizationId || ""}
        disabled={loading || organizations.length < 2}
        onChange={(event) => switchOrganization(event.target.value)}
        title={label}
      >
        {organizations.map((org) => (
          <option key={org.id} value={org.id}>
            {org.branding?.displayName || org.name}
          </option>
        ))}
      </select>
    </label>
  );
}
