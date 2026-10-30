import { Link } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <div className="app-shell px-4 py-10">
      <div className="max-w-lg mx-auto app-surface rounded-lg p-6 sm:p-8">
        <p className="text-sm app-muted mb-2">404</p>
        <h1 className="text-2xl font-bold mb-3">Page not found</h1>
        <p className="app-muted mb-6">That address is not part of CoderzClub.</p>
        <Link to="/" className="inline-flex px-4 py-2 rounded bg-blue-600 text-white">
          Go to home
        </Link>
      </div>
    </div>
  );
}
