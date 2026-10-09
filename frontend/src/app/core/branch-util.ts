import { Branch } from './models';

/** A record with no branch (older than branches) belongs to the head office. Returns null only when branches aren't loaded / there is no head office. */
export function effectiveBranchId(recordBranchId: number | null | undefined, branches: Branch[]): number | null {
  if (recordBranchId != null) return recordBranchId;
  return branches.find((b) => b.headquarters)?.id ?? null;
}

export function branchNameOf(recordBranchId: number | null | undefined, branches: Branch[]): string {
  const id = effectiveBranchId(recordBranchId, branches);
  return branches.find((b) => b.id === id)?.name ?? '—';
}
