export function selectedClaim() {
  try {
    const value = JSON.parse(sessionStorage.getItem('nexus-selected-claim') ?? 'null');
    if (value && typeof value.id === 'string' && typeof value.number === 'string') return value as { id: string; number: string };
  } catch { /* The navigation still works when session storage is unavailable. */ }
  return null;
}
