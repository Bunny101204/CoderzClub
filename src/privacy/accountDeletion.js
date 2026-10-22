export const DELETE_CONFIRMATION_PHRASE = "DELETE";

export function canConfirmAccountDeletion(value) {
  return String(value || "").trim() === DELETE_CONFIRMATION_PHRASE;
}

export function clearLocalAuthState() {
  localStorage.removeItem("jwtToken");
  localStorage.removeItem("token");
  if (typeof sessionStorage !== "undefined") {
    sessionStorage.removeItem("jwtToken");
    sessionStorage.removeItem("token");
  }
}
