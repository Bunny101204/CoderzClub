// import React, { useState, useEffect, useMemo } from "react";
// import axios from "axios";
// import { useAuth } from "../context/AuthContext";
// import { useNavigate } from "react-router-dom";

// const Profile = ({ isOpen, onClose, asPage = false }) => {
//   const { user, logout } = useAuth();
//   const navigate = useNavigate();
//   const [isLoggingOut, setIsLoggingOut] = useState(false);
//   const [stats, setStats] = useState(null);
//   const [submissions, setSubmissions] = useState([]);
//   const [problems, setProblems] = useState([]);
//   const [loadingStats, setLoadingStats] = useState(true);
//   const [statsError, setStatsError] = useState(null);

//   useEffect(() => {
//     if (!asPage && !isOpen) return;
//     const token =
//       localStorage.getItem("jwtToken") || localStorage.getItem("token");
//     const headers = token ? { Authorization: `Bearer ${token}` } : {};

//     const fetchAll = async () => {
//       try {
//         setLoadingStats(true);
//         setStatsError(null);
//         const [statsRes, subsRes, probsRes] = await Promise.all([
//           axios
//             .get("/api/users/stats", { headers })
//             .catch(() => ({ data: null })),
//           axios
//             .get("/api/submissions/my-submissions", { headers })
//             .catch(() => ({ data: [] })),
//           axios.get("/api/problems").catch(() => ({ data: [] })),
//         ]);
//         setStats(statsRes.data);
//         setSubmissions(Array.isArray(subsRes.data) ? subsRes.data : []);
//         setProblems(Array.isArray(probsRes.data) ? probsRes.data : []);
//       } catch (e) {
//         console.error("Error fetching profile data:", e);
//         setStatsError("Failed to load stats");
//         setStats(null);
//         setSubmissions([]);
//         setProblems([]);
//       } finally {
//         setLoadingStats(false);
//       }
//     };

//     fetchAll();

//     // Refresh data every 30 seconds for dynamic updates
//     const interval = setInterval(() => {
//       fetchAll();
//     }, 30000);

//     return () => clearInterval(interval);
//   }, [isOpen, asPage]);

//   const handleLogout = async () => {
//     setIsLoggingOut(true);
//     logout();
//     if (onClose) onClose();
//     navigate("/auth");
//     setIsLoggingOut(false);
//   };

//   if (!asPage && !isOpen) return null;

//   // Build maps and aggregates
//   const problemMap = useMemo(() => {
//     const map = new Map();
//     (problems || []).forEach((p) => map.set(String(p.id), p));
//     return map;
//   }, [problems]);

//   const difficultyCounts = useMemo(() => {
//     const counts = {
//       BASIC: 0,
//       INTERMEDIATE: 0,
//       ADVANCED: 0,
//       SDE: 0,
//       EXPERT: 0,
//       UNKNOWN: 0,
//     };
//     if (!submissions || !Array.isArray(submissions)) return counts;
//     const accepted = submissions.filter(
//       (s) =>
//         s &&
//         (s.result === "ACCEPTED" ||
//           s.status === "ACCEPTED" ||
//           s.verdict === "ACCEPTED")
//     );
//     accepted.forEach((s) => {
//       if (!s || !s.problemId) return;
//       const p = problemMap.get(String(s.problemId));
//       const diff = p?.difficulty || "UNKNOWN";
//       counts[diff] = (counts[diff] || 0) + 1;
//     });
//     return counts;
//   }, [submissions, problemMap]);

//   const activityByDay = useMemo(() => {
//     const counts = new Map();
//     if (!submissions || !Array.isArray(submissions)) return counts;
//     submissions.forEach((s) => {
//       if (!s || !s.createdAt) return;
//       try {
//         const d = new Date(s.createdAt);
//         if (isNaN(d.getTime())) return;
//         const key = new Date(
//           Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())
//         )
//           .toISOString()
//           .slice(0, 10);
//         counts.set(key, (counts.get(key) || 0) + 1);
//       } catch (e) {
//         console.warn("Invalid date in submission:", s.createdAt);
//       }
//     });
//     return counts;
//   }, [submissions]);

//   // Build last 180 days object with days array and monthLabels for rendering
//   const lastDays = useMemo(() => {
//     const days = [];
//     const monthLabels = [];
//     const today = new Date();
//     for (let i = 179; i >= 0; i--) {
//       const d = new Date(today);
//       d.setDate(today.getDate() - i);
//       const key = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))
//         .toISOString()
//         .slice(0, 10);
//       days.push({ key, date: d, count: activityByDay.get(key) || 0 });
//     }

//     // Create month labels - show label at the index where the month starts (or first item)
//     days.forEach((day, idx) => {
//       if (idx === 0 || day.date.getDate() === 1) {
//         monthLabels.push({
//           index: idx,
//           label: day.date.toLocaleString(undefined, { month: "short" }),
//         });
//       }
//     });

//     return { days, monthLabels };
//   }, [activityByDay]);

//   // heatColor: 0 => grey, 1..4 => progressively greener, >=5 => greenest
//   const heatColor = (count) => {
//     if (!count || count <= 0) return "bg-gray-800";
//     if (count === 1) return "bg-green-900";
//     if (count === 2) return "bg-green-700";
//     if (count === 3) return "bg-green-500";
//     if (count === 4) return "bg-green-400";
//     return "bg-green-300"; // 5 or more -> greenest
//   };

//   const Wrapper = ({ children }) =>
//     asPage ? (
//       <div className="min-h-screen bg-gray-900 text-white p-6">{children}</div>
//     ) : (
//       <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
//         {children}
//       </div>
//     );

//   return (
//     <Wrapper>
//       <div
//         className={asPage ? "max-w-7xl mx-auto" : "w-full max-w-2xl mx-auto"}
//       >
//         <h2 className="text-2xl font-bold mb-6">Profile</h2>
//         <div className="grid grid-cols-1 lg:grid-cols-10 gap-6">
//           {/* Left - 30% */}
//           <div className="lg:col-span-3 space-y-4">
//             <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
//               <div className="flex items-center space-x-3 mb-4">
//                 <div className="w-12 h-12 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full flex items-center justify-center">
//                   <span className="text-white font-bold text-lg">
//                     {user?.username?.charAt(0).toUpperCase()}
//                   </span>
//                 </div>
//                 <div>
//                   <h3 className="text-white font-semibold">{user?.username}</h3>
//                 </div>
//               </div>
//               <div className="space-y-3 text-sm">
//                 <div className="flex justify-between py-2 border-b border-gray-700">
//                   <span className="text-gray-300">Username</span>
//                   <span className="text-white font-medium">
//                     {user?.username}
//                   </span>
//                 </div>
//               </div>
//               <div className="pt-4">
//                 <button
//                   onClick={handleLogout}
//                   disabled={isLoggingOut}
//                   className="w-full bg-red-600 hover:bg-red-700 disabled:bg-red-800 text-white font-semibold py-3 px-4 rounded-lg transition-colors disabled:cursor-not-allowed"
//                 >
//                   {isLoggingOut ? "Logging Out..." : "Logout"}
//                 </button>
//               </div>
//             </div>

//             {/* Quick stats card */}
//             <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
//               <h3 className="text-white font-semibold mb-3">Overview</h3>
//               {loadingStats ? (
//                 <div className="text-gray-400 text-sm">Loading stats...</div>
//               ) : statsError ? (
//                 <div className="text-red-400 text-sm">{statsError}</div>
//               ) : stats ? (
//                 <div className="grid grid-cols-2 gap-3 text-sm">
//                   <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
//                     <div className="text-gray-400">Points</div>
//                     <div className="text-green-400 text-xl font-bold">
//                       {stats.totalPoints}
//                     </div>
//                   </div>
//                   <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
//                     <div className="text-gray-400">Solved</div>
//                     <div className="text-blue-400 text-xl font-bold">
//                       {stats.totalProblemsSolved}
//                     </div>
//                   </div>
//                   <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
//                     <div className="text-gray-400">Success</div>
//                     <div className="text-yellow-400 text-xl font-bold">
//                       {Math.round((stats.successRate || 0) * 100)}%
//                     </div>
//                   </div>
//                   <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
//                     <div className="text-gray-400">Streak</div>
//                     <div className="text-orange-400 text-xl font-bold">
//                       {stats.currentStreak}
//                     </div>
//                   </div>
//                 </div>
//               ) : (
//                 <div className="text-gray-400 text-sm">No stats available.</div>
//               )}
//             </div>
//           </div>

//           {/* Right - 70% */}
//           <div className="lg:col-span-7 space-y-6">
//             {/* Total Solved visual */}
//             <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
//               <div className="flex items-center justify-between">
//                 <h3 className="text-white font-semibold">Problems Solved</h3>
//                 <div className="text-4xl font-extrabold text-blue-400">
//                   {stats?.totalProblemsSolved ?? 0}
//                 </div>
//               </div>
//             </div>

//             {/* Difficulty distribution */}
//             <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
//               <h3 className="text-white font-semibold mb-4">
//                 Solved by Difficulty
//               </h3>
//               <div className="space-y-3">
//                 {["BASIC", "INTERMEDIATE", "ADVANCED", "SDE", "EXPERT"].map(
//                   (d) => (
//                     <div key={d} className="flex items-center gap-3">
//                       <div className="w-32 text-sm text-gray-300">{d}</div>
//                       <div className="flex-1 bg-gray-900 rounded h-3 overflow-hidden">
//                         <div
//                           className={`h-3 ${
//                             d === "BASIC"
//                               ? "bg-green-500"
//                               : d === "INTERMEDIATE"
//                               ? "bg-yellow-500"
//                               : d === "ADVANCED"
//                               ? "bg-orange-500"
//                               : d === "SDE"
//                               ? "bg-red-500"
//                               : "bg-purple-500"
//                           }`}
//                           style={{
//                             width: `${Math.min(
//                               ((difficultyCounts[d] || 0) /
//                                 Math.max(1, stats?.totalProblemsSolved || 0)) *
//                                 100,
//                               100
//                             )}%`,
//                           }}
//                         />
//                       </div>
//                       <div className="w-10 text-right text-sm text-gray-300">
//                         {difficultyCounts[d] || 0}
//                       </div>
//                     </div>
//                   )
//                 )}
//               </div>
//             </div>

//             {/* Activity heatmap */}
//             <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
//               <div className="flex items-center justify-between mb-4">
//                 <h3 className="text-white font-semibold">
//                   Active Days (Last 6 months)
//                 </h3>
//                 <div className="text-sm text-gray-400">
//                   A day is active if it has ≥1 submission
//                 </div>
//               </div>
//               <div className="relative">
//                 {/* Month labels */}
//                 <div className="relative h-4 mb-2 text-xs text-gray-400">
//                   {lastDays.monthLabels.map((ml, idx) => (
//                     <div
//                       key={idx}
//                       className="absolute -top-1 text-xs"
//                       style={{
//                         left: `${(ml.index / 180) * 100}%`,
//                         transform: "translateX(-2px)",
//                       }}
//                     >
//                       {ml.label}
//                     </div>
//                   ))}
//                 </div>
//                 {/* Days grid: 30 columns x 6 rows = 180 days */}
//                 <div
//                   className="grid gap-1"
//                   style={{ gridTemplateColumns: "repeat(30, minmax(0, 1fr))" }}
//                 >
//                   {lastDays.days.map((d, idx) => (
//                     <div
//                       key={idx}
//                       className={`w-4 h-4 ${heatColor(
//                         d.count
//                       )} rounded border border-gray-700`}
//                       title={`${d.key}: ${d.count} submission(s)`}
//                     />
//                   ))}
//                 </div>
//               </div>
//               <div className="flex items-center gap-2 mt-3 text-xs text-gray-400">
//                 <span>Less</span>
//                 <span className="w-4 h-4 bg-gray-800 rounded border border-gray-700 inline-block"></span>
//                 <span className="w-4 h-4 bg-green-900 rounded border border-gray-700 inline-block"></span>
//                 <span className="w-4 h-4 bg-green-700 rounded border border-gray-700 inline-block"></span>
//                 <span className="w-4 h-4 bg-green-500 rounded border border-gray-700 inline-block"></span>
//                 <span className="w-4 h-4 bg-green-400 rounded border border-gray-700 inline-block"></span>
//                 <span className="w-4 h-4 bg-green-300 rounded border border-gray-700 inline-block"></span>
//                 <span>More</span>
//               </div>
//             </div>
//           </div>
//         </div>
//       </div>
//     </Wrapper>
//   );
// };

// export default Profile;

import React, { useState, useEffect, useMemo } from "react";
import axios from "axios";
import { useAuth } from "../context/AuthContext";
import { useNavigate } from "react-router-dom";

const Profile = ({ isOpen, onClose, asPage = false }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [stats, setStats] = useState(null);
  const [submissions, setSubmissions] = useState([]);
  const [problems, setProblems] = useState([]);
  const [loadingStats, setLoadingStats] = useState(true);
  const [statsError, setStatsError] = useState(null);

  useEffect(() => {
    if (!asPage && !isOpen) return;
    const token =
      localStorage.getItem("jwtToken") || localStorage.getItem("token");
    const headers = token ? { Authorization: `Bearer ${token}` } : {};

    const fetchAll = async () => {
      try {
        setLoadingStats(true);
        setStatsError(null);
        const [statsRes, subsRes, probsRes] = await Promise.all([
          axios
            .get("/api/users/stats", { headers })
            .catch(() => ({ data: null })),
          axios
            .get("/api/submissions/my-submissions", { headers })
            .catch(() => ({ data: [] })),
          axios.get("/api/problems").catch(() => ({ data: [] })),
        ]);
        setStats(statsRes.data);
        setSubmissions(Array.isArray(subsRes.data) ? subsRes.data : []);
        setProblems(Array.isArray(probsRes.data) ? probsRes.data : []);
      } catch (e) {
        console.error("Error fetching profile data:", e);
        setStatsError("Failed to load stats");
        setStats(null);
        setSubmissions([]);
        setProblems([]);
      } finally {
        setLoadingStats(false);
      }
    };

    fetchAll();

    // Refresh data every 30 seconds for dynamic updates
    const interval = setInterval(() => {
      fetchAll();
    }, 30000);

    return () => clearInterval(interval);
  }, [isOpen, asPage]);

  const handleLogout = async () => {
    setIsLoggingOut(true);
    logout();
    if (onClose) onClose();
    navigate("/auth");
    setIsLoggingOut(false);
  };

  if (!asPage && !isOpen) return null;

  // Build maps and aggregates
  const problemMap = useMemo(() => {
    const map = new Map();
    (problems || []).forEach((p) => map.set(String(p.id), p));
    return map;
  }, [problems]);

  const difficultyCounts = useMemo(() => {
    const counts = {
      BASIC: 0,
      INTERMEDIATE: 0,
      ADVANCED: 0,
      SDE: 0,
      EXPERT: 0,
      UNKNOWN: 0,
    };
    if (!submissions || !Array.isArray(submissions)) return counts;
    const accepted = submissions.filter(
      (s) =>
        s &&
        (s.result === "ACCEPTED" ||
          s.status === "ACCEPTED" ||
          s.verdict === "ACCEPTED")
    );
    accepted.forEach((s) => {
      if (!s || !s.problemId) return;
      const p = problemMap.get(String(s.problemId));
      const diff = p?.difficulty || "UNKNOWN";
      counts[diff] = (counts[diff] || 0) + 1;
    });
    return counts;
  }, [submissions, problemMap]);

  const activityByDay = useMemo(() => {
    const counts = new Map();
    if (!submissions || !Array.isArray(submissions)) return counts;
    submissions.forEach((s) => {
      if (!s || !s.createdAt) return;
      try {
        const d = new Date(s.createdAt);
        if (isNaN(d.getTime())) return;
        const key = new Date(
          Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())
        )
          .toISOString()
          .slice(0, 10);
        counts.set(key, (counts.get(key) || 0) + 1);
      } catch (e) {
        console.warn("Invalid date in submission:", s.createdAt);
      }
    });
    return counts;
  }, [submissions]);

  // New heatmap logic: build weeks (columns) x 7 days (rows) for the last 365 days
  const heatmap = useMemo(() => {
    const today = new Date();
    // Normalize to UTC date (no time offset)
    const utcToday = new Date(
      Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate())
    );

    // Start 364 days ago (so total 365 days including today)
    const startDate = new Date(utcToday);
    startDate.setUTCDate(utcToday.getUTCDate() - 364);

    // Back up to the previous Sunday so weeks start on Sunday (like GitHub)
    const startOfWeek = new Date(startDate);
    startOfWeek.setUTCDate(startDate.getUTCDate() - startDate.getUTCDay());

    // Build weeks array
    const weeks = [];
    const cursor = new Date(startOfWeek);
    while (cursor <= utcToday) {
      const week = [];
      for (let i = 0; i < 7; i++) {
        const key = new Date(
          Date.UTC(
            cursor.getUTCFullYear(),
            cursor.getUTCMonth(),
            cursor.getUTCDate()
          )
        )
          .toISOString()
          .slice(0, 10);
        week.push({
          key,
          date: new Date(cursor),
          count: activityByDay.get(key) || 0,
        });
        cursor.setUTCDate(cursor.getUTCDate() + 1);
      }
      weeks.push(week);
    }

    // Compute month labels positioned at the week index where a month starts (or the first week containing the 1st)
    const monthLabels = [];
    let prevMonth = -1;
    weeks.forEach((week, weekIdx) => {
      // Prefer to label the week with the day that is the 1st of a month if present, otherwise use the week's first day.
      const firstOfMonthDay = week.find((d) => d.date.getDate() === 1);
      const labelDay = firstOfMonthDay || week[0];
      const month = labelDay.date.getMonth();
      if (month !== prevMonth) {
        monthLabels.push({
          index: weekIdx,
          label: labelDay.date.toLocaleString(undefined, { month: "short" }),
        });
        prevMonth = month;
      }
    });

    // Maximum count to scale colors
    let max = 0;
    weeks.forEach((w) => w.forEach((d) => (max = Math.max(max, d.count))));

    return { weeks, monthLabels, max };
  }, [activityByDay]);

  // heatColor: scale counts relative to max (4 intensity buckets)
  const heatColor = (count, max) => {
    if (!count || count === 0) return "bg-gray-800";
    // intensity from 1..4
    const intensity = Math.ceil((count / Math.max(1, max)) * 4);
    if (intensity <= 1) return "bg-green-900";
    if (intensity === 2) return "bg-green-700";
    if (intensity === 3) return "bg-green-500";
    return "bg-green-300";
  };

  const Wrapper = ({ children }) =>
    asPage ? (
      <div className="min-h-screen bg-gray-900 text-white p-6">{children}</div>
    ) : (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
        {children}
      </div>
    );

  return (
    <Wrapper>
      <div
        className={asPage ? "max-w-7xl mx-auto" : "w-full max-w-2xl mx-auto"}
      >
        <h2 className="text-2xl font-bold mb-6">Profile</h2>
        <div className="grid grid-cols-1 lg:grid-cols-10 gap-6">
          {/* Left - 30% */}
          <div className="lg:col-span-3 space-y-4">
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <div className="flex items-center space-x-3 mb-4">
                <div className="w-12 h-12 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full flex items-center justify-center">
                  <span className="text-white font-bold text-lg">
                    {user?.username?.charAt(0).toUpperCase()}
                  </span>
                </div>
                <div>
                  <h3 className="text-white font-semibold">{user?.username}</h3>
                </div>
              </div>
              <div className="space-y-3 text-sm">
                <div className="flex justify-between py-2 border-b border-gray-700">
                  <span className="text-gray-300">Username</span>
                  <span className="text-white font-medium">
                    {user?.username}
                  </span>
                </div>
              </div>
              <div className="pt-4">
                <button
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                  className="w-full bg-red-600 hover:bg-red-700 disabled:bg-red-800 text-white font-semibold py-3 px-4 rounded-lg transition-colors disabled:cursor-not-allowed"
                >
                  {isLoggingOut ? "Logging Out..." : "Logout"}
                </button>
              </div>
            </div>

            {/* Quick stats card */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <h3 className="text-white font-semibold mb-3">Overview</h3>
              {loadingStats ? (
                <div className="text-gray-400 text-sm">Loading stats...</div>
              ) : statsError ? (
                <div className="text-red-400 text-sm">{statsError}</div>
              ) : stats ? (
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Points</div>
                    <div className="text-green-400 text-xl font-bold">
                      {stats.totalPoints}
                    </div>
                  </div>
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Solved</div>
                    <div className="text-blue-400 text-xl font-bold">
                      {stats.totalProblemsSolved}
                    </div>
                  </div>
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Success</div>
                    <div className="text-yellow-400 text-xl font-bold">
                      {Math.round((stats.successRate || 0) * 100)}%
                    </div>
                  </div>
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Streak</div>
                    <div className="text-orange-400 text-xl font-bold">
                      {stats.currentStreak}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="text-gray-400 text-sm">No stats available.</div>
              )}
            </div>
          </div>

          {/* Right - 70% */}
          <div className="lg:col-span-7 space-y-6">
            {/* Total Solved visual */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <div className="flex items-center justify-between">
                <h3 className="text-white font-semibold">Problems Solved</h3>
                <div className="text-4xl font-extrabold text-blue-400">
                  {stats?.totalProblemsSolved ?? 0}
                </div>
              </div>
            </div>

            {/* Difficulty distribution */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <h3 className="text-white font-semibold mb-4">
                Solved by Difficulty
              </h3>
              <div className="space-y-3">
                {["BASIC", "INTERMEDIATE", "ADVANCED", "SDE", "EXPERT"].map(
                  (d) => (
                    <div key={d} className="flex items-center gap-3">
                      <div className="w-32 text-sm text-gray-300">{d}</div>
                      <div className="flex-1 bg-gray-900 rounded h-3 overflow-hidden">
                        <div
                          className={`h-3 ${
                            d === "BASIC"
                              ? "bg-green-500"
                              : d === "INTERMEDIATE"
                              ? "bg-yellow-500"
                              : d === "ADVANCED"
                              ? "bg-orange-500"
                              : d === "SDE"
                              ? "bg-red-500"
                              : "bg-purple-500"
                          }`}
                          style={{
                            width: `${Math.min(
                              ((difficultyCounts[d] || 0) /
                                Math.max(1, stats?.totalProblemsSolved || 0)) *
                                100,
                              100
                            )}%`,
                          }}
                        />
                      </div>
                      <div className="w-10 text-right text-sm text-gray-300">
                        {difficultyCounts[d] || 0}
                      </div>
                    </div>
                  )
                )}
              </div>
            </div>

            {/* Activity heatmap (yearly, week columns) */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-white font-semibold">
                  Active Days (Last 12 months)
                </h3>
                <div className="text-sm text-gray-400">
                  A day is active if it has ≥1 submission
                </div>
              </div>

              <div className="relative">
                {/* Month labels */}
                <div className="relative h-5 mb-2 text-xs text-gray-400">
                  {heatmap.monthLabels.map((ml, idx) => (
                    <div
                      key={idx}
                      className="absolute -top-1 text-xs"
                      style={{
                        left: `${
                          (ml.index / Math.max(1, heatmap.weeks.length - 1)) *
                          100
                        }%`,
                        transform: "translateX(-2px)",
                      }}
                    >
                      {ml.label}
                    </div>
                  ))}
                </div>

                {/* Grid: columns = weeks, each column stacks 7 days */}
                <div className="overflow-x-auto -mx-2 px-2">
                  {" "}
                  {/* allow horizontal scroll on small screens */}
                  <div
                    className="grid gap-1"
                    style={{
                      gridAutoFlow: "column",
                      gridAutoColumns: "min-content",
                      alignItems: "start",
                      // small left/right padding keeps it compact
                    }}
                  >
                    {heatmap.weeks.map((week, wIdx) => (
                      <div key={wIdx} className="flex flex-col gap-1">
                        {week.map((d, dayIdx) => (
                          <div
                            key={dayIdx}
                            className={`${heatColor(
                              d.count,
                              heatmap.max
                            )} w-3 h-3 rounded-sm border border-gray-700 transition-transform transform hover:scale-110`}
                            title={`${d.key}: ${d.count} submission(s)`}
                          />
                        ))}
                      </div>
                    ))}
                  </div>
                </div>

                <div className="flex items-center gap-2 mt-3 text-xs text-gray-400">
                  <span>Less</span>
                  <span className="w-3 h-3 bg-gray-800 rounded border border-gray-700 inline-block"></span>
                  <span className="w-3 h-3 bg-green-900 rounded border border-gray-700 inline-block"></span>
                  <span className="w-3 h-3 bg-green-700 rounded border border-gray-700 inline-block"></span>
                  <span className="w-3 h-3 bg-green-500 rounded border border-gray-700 inline-block"></span>
                  <span className="w-3 h-3 bg-green-300 rounded border border-gray-700 inline-block"></span>
                  <span>More</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Wrapper>
  );
};

export default Profile;
