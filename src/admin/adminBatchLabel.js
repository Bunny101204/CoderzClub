export function batchTabLabel(batches) {
  if (!Array.isArray(batches)) {
    return "Batches";
  }
  return `Batches (${batches.length})`;
}
