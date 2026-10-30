import { Navigate, useParams } from "react-router-dom";
import { legacyProblemTarget } from "./legacyProblem.js";

export default function LegacyProblemRedirect() {
  const { id } = useParams();
  return <Navigate to={legacyProblemTarget(id)} replace />;
}
