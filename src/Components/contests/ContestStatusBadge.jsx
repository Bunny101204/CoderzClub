const STYLES = {
  DRAFT: "bg-gray-200 text-gray-800 dark:bg-gray-600 dark:text-white",
  UPCOMING: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-100",
  LIVE: "bg-green-500 text-white",
  ENDED: "bg-gray-500 text-white",
};

export default function ContestStatusBadge({ status }) {
  const value = status || "DRAFT";
  return (
    <span className={`inline-flex px-2 py-1 rounded-full text-xs font-semibold ${STYLES[value] || STYLES.DRAFT}`}>
      {value}
    </span>
  );
}
