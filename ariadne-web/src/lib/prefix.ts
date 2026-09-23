export function normalizePrefix(value: string): string {
  return value.replace(/\D/g, '').slice(0, 6)
}

export function isSearchablePrefix(value: string): boolean {
  return /^\d{3,6}$/.test(value)
}
