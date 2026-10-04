import React from "react";
import { Link } from "react-router-dom";

const TermsPage = () => (
  <div className="app-shell p-8">
    <div className="max-w-3xl mx-auto space-y-6">
      <Link to="/" className="text-blue-600 dark:text-blue-400 hover:underline">← Home</Link>
      <h1 className="text-3xl font-bold">Terms of Service</h1>
      <p>
        These notes describe how CoderzClub currently works. They are not a complete legal agreement and do not set jurisdiction, liability, or payment terms.
      </p>
      <section>
        <h2 className="text-xl font-semibold mb-2">Using the service</h2>
        <ul className="list-disc pl-6 space-y-1">
          <li>You are responsible for the credentials you use to sign in.</li>
          <li>The platform is for practicing programming problems and related classroom batch features.</li>
          <li>Do not use the service to attack infrastructure, bypass access controls, or abuse code execution resources.</li>
          <li>Code you submit is executed for judging and stored as part of your submission history.</li>
          <li>Availability is not guaranteed; execution and queueing may fail or be delayed.</li>
          <li>You can request account deletion from Profile. In the application that action cannot be undone.</li>
        </ul>
      </section>
    </div>
  </div>
);

export default TermsPage;
