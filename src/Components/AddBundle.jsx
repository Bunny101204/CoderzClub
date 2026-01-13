import React, { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";

const AddBundle = () => {
  const { id } = useParams();
  const bundleId = id;

  //form to submit new bundle
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    difficulty: "",
    category: "",
    tags: "",
    isPremium: false,
    price: 0,
    currency: "USD",
    sharedTemplate: "",
    isActive: true,
  });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const difficulties = ["BASIC", "INTERMEDIATE", "ADVANCED", "SDE", "EXPERT"];
  const categories = [
    "ALGORITHMS",
    "DATA_STRUCTURES",
    "SYSTEM_DESIGN",
    "DATABASE",
    "WEB_DEVELOPMENT",
  ];

  useEffect(() => {
    if (bundleId) {
      fetchBundle();
    }
  }, [bundleId]);

  const fetchBundle = async () => {
    try {
      const response = await fetch(`/api/bundles/${bundleId}`);
      if (response.ok) {
        const bundle = await response.json();
        setFormData({
          name: bundle.name || "",
          description: bundle.description || "",
          difficulty: bundle.difficulty || "",
          category: bundle.category || "",
          tags: bundle.tags ? bundle.tags.join(", ") : "",
          isPremium: bundle.isPremium || false,
          price: bundle.price || 0,
          currency: bundle.currency || "USD",
          sharedTemplate: bundle.sharedTemplate || "",
          isActive: bundle.isActive !== undefined ? bundle.isActive : true,
        });
      }
    } catch (error) {
      console.error("Error fetching bundle:", error);
      setError("Failed to load bundle data");
    }
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setLoading(true);

    if (
      !formData.name ||
      !formData.description ||
      !formData.difficulty ||
      !formData.category
    ) {
      setError("Please fill in all required fields.");
      setLoading(false);
      return;
    }

    try {
      const bundleData = {
        name: formData.name,
        description: formData.description,
        difficulty: formData.difficulty,
        category: formData.category,
        tags: formData.tags
          .split(",")
          .map((tag) => tag.trim())
          .filter(Boolean),
        isPremium: !!formData.isPremium,
        price: Number(formData.price || 0),
        currency: formData.currency,
        sharedTemplate: formData.sharedTemplate,
        isActive: !!formData.isActive,
        totalProblems: 0,
        totalPoints: 0,
        estimatedTotalTime: 0,
        problemIds: [],
      };

      const url = bundleId ? `/api/bundles/${bundleId}` : "/api/bundles";
      const method = bundleId ? "PUT" : "POST";

      // Get JWT token for authentication
      const token = localStorage.getItem("jwtToken");
      const headers = {
        "Content-Type": "application/json",
        Accept: "application/json",
      };

      // Add Authorization header if token exists
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      const response = await fetch(url, {
        method,
        headers,
        body: JSON.stringify(bundleData),
      });

      if (!response.ok) {
        let serverMsg = "";
        try {
          serverMsg = await response.text();
        } catch {}

        console.error(
          `[AddBundle] ${method} ${url} failed`,
          response.status,
          serverMsg
        );

        // Provide specific error messages based on status code
        let errorMessage = "";
        switch (response.status) {
          case 401:
            errorMessage =
              "Authentication required. Please log in as an admin.";
            break;
          case 403:
            errorMessage = "Access denied. Admin privileges required.";
            break;
          case 400:
            errorMessage =
              serverMsg || "Invalid data provided. Please check all fields.";
            break;
          case 500:
            errorMessage = "Server error. Please try again later.";
            break;
          default:
            errorMessage =
              serverMsg || `Failed to ${bundleId ? "update" : "create"} bundle`;
        }

        throw new Error(errorMessage);
      }

      setSuccess(`Bundle ${bundleId ? "updated" : "created"} successfully!`);
      setTimeout(() => navigate("/admin"), 1500);
    } catch (err) {
      setError(
        err && err.message
          ? err.message
          : `Failed to ${bundleId ? "update" : "create"} bundle.`
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center p-4">
      <form
        onSubmit={handleSubmit}
        className="bg-gray-800 p-8 rounded-lg shadow-lg w-full max-w-2xl"
      >
        <h2 className="text-2xl font-bold mb-6 text-white text-center">
          {bundleId ? "Edit Bundle" : "Add New Bundle"}
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-white mb-2">Name*</label>
            <input
              type="text"
              name="name"
              value={formData.name}
              onChange={handleInputChange}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500"
              placeholder="Bundle name"
            />
          </div>

          <div>
            <label className="block text-white mb-2">Difficulty*</label>
            <select
              name="difficulty"
              value={formData.difficulty}
              onChange={handleInputChange}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500"
            >
              <option value="">Select difficulty</option>
              {difficulties.map((diff) => (
                <option key={diff} value={diff}>
                  {diff}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="mt-4">
          <label className="block text-white mb-2">Description*</label>
          <textarea
            name="description"
            value={formData.description}
            onChange={handleInputChange}
            className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[100px]"
            placeholder="Bundle description"
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <div>
            <label className="block text-white mb-2">Category*</label>
            <select
              name="category"
              value={formData.category}
              onChange={handleInputChange}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500"
            >
              <option value="">Select category</option>
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {cat.replace("_", " ")}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-white mb-2">
              Tags (comma separated)
            </label>
            <input
              type="text"
              name="tags"
              value={formData.tags}
              onChange={handleInputChange}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500"
              placeholder="beginner, algorithms, fundamentals"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
          <div className="flex items-center">
            <input
              type="checkbox"
              name="isPremium"
              checked={formData.isPremium}
              onChange={handleInputChange}
              className="mr-2"
            />
            <label className="text-white">Premium Bundle</label>
          </div>

          <div>
            <label className="block text-white mb-2">Price ($)</label>
            <input
              type="number"
              name="price"
              value={formData.price}
              onChange={handleInputChange}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500"
              min="0"
              step="0.01"
              disabled={!formData.isPremium}
            />
          </div>

          <div>
            <label className="block text-white mb-2">Currency</label>
            <select
              name="currency"
              value={formData.currency}
              onChange={handleInputChange}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500"
              disabled={!formData.isPremium}
            >
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
            </select>
          </div>
        </div>

        <div className="mt-4">
          <label className="block text-white mb-2">Shared Template Code</label>
          <div className="text-gray-400 text-xs mb-2">
            This template will be used for all problems in this bundle. Use
            placeholders like {"{functionName}"} and {"{parameters}"} for
            dynamic content.
          </div>
          <textarea
            name="sharedTemplate"
            value={formData.sharedTemplate}
            onChange={handleInputChange}
            className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[150px] font-mono text-sm"
            placeholder="public class Solution {&#10;    public int solve(int[] nums) {&#10;        // Your code here&#10;        return 0;&#10;    }&#10;}"
          />
        </div>

        <div className="flex items-center mt-4">
          <input
            type="checkbox"
            name="isActive"
            checked={formData.isActive}
            onChange={handleInputChange}
            className="mr-2"
          />
          <label className="text-white">Active (available to users)</label>
        </div>

        {error && <div className="text-red-400 mt-4 text-center">{error}</div>}
        {success && (
          <div className="text-green-400 mt-4 text-center">{success}</div>
        )}

        <div className="flex gap-4 mt-6">
          <button
            type="submit"
            className="flex-1 bg-green-500 hover:bg-green-600 text-white font-bold py-3 px-4 rounded transition-colors"
            disabled={loading}
          >
            {loading
              ? "Saving..."
              : bundleId
              ? "Update Bundle"
              : "Create Bundle"}
          </button>
          <button
            type="button"
            onClick={() => navigate("/admin")}
            className="px-6 py-3 bg-gray-600 hover:bg-gray-700 text-white rounded transition-colors"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default AddBundle;
