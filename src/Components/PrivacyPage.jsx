import React from "react";
import { Link } from "react-router-dom";

const PrivacyPage = () => (
  <div className="app-shell p-8">
    <div className="max-w-3xl mx-auto space-y-6">
      <Link to="/" className="text-blue-600 dark:text-blue-400 hover:underline">← Home</Link>
      <h1 className="text-3xl font-bold">Privacy</h1>
      <p>
        This page describes how the current CoderzClub application stores and uses account data.
        It is a product description of implemented behavior, not a complete legal privacy policy.
      </p>
      <section>
        <h2 className="text-xl font-semibold mb-2">Information stored by the application</h2>
        <ul className="list-disc pl-6 space-y-1">
          <li>Account username, email, a hashed password, and optional profile fields such as bio, location, and website.</li>
          <li>Coding submissions, including source code, language, and judged results.</li>
          <li>Progress and activity derived from judged submissions.</li>
          <li>Classroom batch membership when an administrator adds you to a batch.</li>
          <li>Operational logs that may include user ids, job ids, and timing metrics.</li>
        </ul>
      </section>
      <section>
        <h2 className="text-xl font-semibold mb-2">What this application does not currently do</h2>
        <ul className="list-disc pl-6 space-y-1">
          <li>It does not process payments or store card details.</li>
          <li>It does not currently specify how long backups or operational logs are kept.</li>
          <li>It does not currently list subprocessors, analytics vendors, or cookie practices.</li>
        </ul>
      </section>
      <section>
        <h2 className="text-xl font-semibold mb-2">Account deletion and export</h2>
        <p>
          From Profile you can download a copy of your account and submission data, or request deletion.
          After confirmed deletion, login is disabled and your username and email are replaced with a non-identifying value.
          Judged submissions may remain under an internal user id so historical judging and reports stay consistent.
          Deletion in the application does not claim to erase backups or every operational log.
        </p>
      </section>
    </div>
  </div>
);

export default PrivacyPage;
