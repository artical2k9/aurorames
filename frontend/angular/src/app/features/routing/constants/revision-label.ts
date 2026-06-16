/**
 * Render a 1-based numeric route revision as an alphabetic label (1 → A, 2 → B, … 27 → AA),
 * so route revisions read the same way as item-master and BOM revisions in the UI. The
 * persisted value stays numeric; this is presentation only.
 */
export function revisionLabel(revision: number | null | undefined): string {
  if (revision == null || revision < 1) return '—';
  let n = Math.floor(revision);
  let label = '';
  while (n > 0) {
    const remainder = (n - 1) % 26;
    label = String.fromCharCode(65 + remainder) + label;
    n = Math.floor((n - 1) / 26);
  }
  return label;
}
