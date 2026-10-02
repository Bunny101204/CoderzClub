import React from "react";
import { Link } from "react-router-dom";

const SiteFooter = () => (
  <footer className="border-t border-gray-800 bg-gray-950 text-gray-400 text-sm">
    <div className="max-w-7xl mx-auto px-4 py-6 flex flex-col sm:flex-row gap-3 sm:items-center sm:justify-between">
      <p>CoderzClub</p>
      <nav className="flex gap-4">
        <Link to="/privacy" className="hover:text-white">Privacy</Link>
        <Link to="/terms" className="hover:text-white">Terms</Link>
      </nav>
    </div>
  </footer>
);

export default SiteFooter;
