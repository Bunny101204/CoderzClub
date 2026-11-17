/**
 * Public reference for Character Frequency ordering.
 * Statement requires lines `char count` ordered by character code ascending
 * (not localeCompare). Empty input yields empty output.
 */
export function characterFrequencyOracle(input) {
  const s = String(input ?? "").replace(/\r?\n$/, "");
  const freq = new Map();
  for (const ch of s) freq.set(ch, (freq.get(ch) || 0) + 1);
  return [...freq.entries()]
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
    .map(([c, n]) => `${c} ${n}`)
    .join("\n");
}
