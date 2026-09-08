import React, { useState } from "react";
import { useAuth } from "../context/AuthContext";

const SubscriptionPlans = () => {
  const { user } = useAuth();
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [loading, setLoading] = useState(false);

  const plans = [
    {
      id: "free",
      name: "Free",
      price: 0,
      currency: "USD",
      period: "Forever",
      description: "Perfect for getting started",
      features: [
        "Access to basic problems",
        "Community support",
        "Basic progress tracking",
        "Limited problem submissions"
      ],
      limitations: [
        "No premium problem bundles",
        "No advanced analytics",
        "No priority support"
      ],
      buttonText: "Current Plan",
      buttonStyle: "bg-gray-600 cursor-not-allowed",
      popular: false
    },
    {
      id: "basic",
      name: "Basic",
      price: 9.99,
      currency: "USD",
      period: "per month",
      description: "Great for regular practice",
      features: [
        "All Free features",
        "Access to intermediate problems",
        "Enhanced progress tracking",
        "Unlimited problem submissions",
        "Basic analytics dashboard"
      ],
      limitations: [
        "No advanced/SDE problems",
        "No priority support"
      ],
      buttonText: "Choose Basic",
      buttonStyle: "bg-blue-600 hover:bg-blue-700",
      popular: false
    },
    {
      id: "premium",
      name: "Premium",
      price: 19.99,
      currency: "USD",
      period: "per month",
      description: "Best for serious developers",
      features: [
        "All Basic features",
        "Access to all problem bundles",
        "Advanced analytics & insights",
        "Priority support",
        "Custom study plans",
        "Interview preparation tools",
        "Certificate of completion"
      ],
      limitations: [],
      buttonText: "Choose Premium",
      buttonStyle: "bg-purple-600 hover:bg-purple-700",
      popular: true
    },
    {
      id: "enterprise",
      name: "Enterprise",
      price: 49.99,
      currency: "USD",
      period: "per month",
      description: "For teams and organizations",
      features: [
        "All Premium features",
        "Team management tools",
        "Advanced reporting",
        "API access",
        "Custom branding",
        "Dedicated support",
        "Training sessions"
      ],
      limitations: [],
      buttonText: "Contact Sales",
      buttonStyle: "bg-green-600 hover:bg-green-700",
      popular: false
    }
  ];

  const handlePlanSelection = async (plan) => {
    if (plan.id === "free" || plan.id === user?.subscriptionPlan) {
      return; // Can't select current plan or free plan
    }

    setSelectedPlan(plan);
    setLoading(true);

    try {
      // Here you would integrate with your payment gateway
      // For now, we'll simulate the process
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      // Redirect to payment or show payment modal
      alert(`Redirecting to payment for ${plan.name} plan...`);
      
    } catch (error) {
      console.error("Error selecting plan:", error);
      alert("Error selecting plan. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const getCurrentPlan = () => {
    return plans.find(plan => plan.id === user?.subscriptionPlan) || plans[0];
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold mb-4">💎 Choose Your Plan</h1>
          <p className="text-gray-400 text-lg">
            Unlock your full potential with our premium features
          </p>
          {user?.subscriptionPlan && user.subscriptionPlan !== "free" && (
            <div className="mt-4 p-4 bg-green-900 border border-green-600 rounded-lg inline-block">
              <span className="text-green-400">✓</span> Current Plan: {getCurrentPlan().name}
            </div>
          )}
        </div>

        {/* Plans Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {plans.map((plan) => {
            const isCurrentPlan = plan.id === user?.subscriptionPlan;
            const isFreePlan = plan.id === "free";
            
            return (
              <div
                key={plan.id}
                className={`relative bg-gray-800 rounded-xl p-6 border-2 transition-all duration-300 ${
                  plan.popular 
                    ? 'border-purple-500 scale-105' 
                    : isCurrentPlan 
                    ? 'border-green-500' 
                    : 'border-gray-700 hover:border-blue-500'
                }`}
              >
                {/* Popular Badge */}
                {plan.popular && (
                  <div className="absolute -top-3 left-1/2 transform -translate-x-1/2">
                    <span className="bg-purple-500 text-white px-4 py-1 rounded-full text-sm font-semibold">
                      Most Popular
                    </span>
                  </div>
                )}

                {/* Current Plan Badge */}
                {isCurrentPlan && (
                  <div className="absolute -top-3 right-4">
                    <span className="bg-green-500 text-white px-3 py-1 rounded-full text-sm font-semibold">
                      Current
                    </span>
                  </div>
                )}

                {/* Plan Header */}
                <div className="text-center mb-6">
                  <h3 className="text-2xl font-bold mb-2">{plan.name}</h3>
                  <div className="mb-2">
                    <span className="text-3xl font-bold">
                      ${plan.price}
                    </span>
                    <span className="text-gray-400 ml-1">{plan.period}</span>
                  </div>
                  <p className="text-gray-400 text-sm">{plan.description}</p>
                </div>

                {/* Features */}
                <div className="mb-6">
                  <h4 className="font-semibold mb-3 text-green-400">✓ What's included:</h4>
                  <ul className="space-y-2">
                    {plan.features.map((feature, index) => (
                      <li key={index} className="text-sm text-gray-300 flex items-start">
                        <span className="text-green-400 mr-2">•</span>
                        {feature}
                      </li>
                    ))}
                  </ul>
                </div>

                {/* Limitations */}
                {plan.limitations.length > 0 && (
                  <div className="mb-6">
                    <h4 className="font-semibold mb-3 text-red-400">✗ Limitations:</h4>
                    <ul className="space-y-2">
                      {plan.limitations.map((limitation, index) => (
                        <li key={index} className="text-sm text-gray-400 flex items-start">
                          <span className="text-red-400 mr-2">•</span>
                          {limitation}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Action Button */}
                <button
                  onClick={() => handlePlanSelection(plan)}
                  disabled={isCurrentPlan || isFreePlan || loading}
                  className={`w-full py-3 px-4 rounded-lg font-semibold transition-colors ${
                    isCurrentPlan || isFreePlan 
                      ? plan.buttonStyle 
                      : plan.buttonStyle
                  } ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  {loading && selectedPlan?.id === plan.id ? (
                    <span className="flex items-center justify-center">
                      <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                      Processing...
                    </span>
                  ) : (
                    plan.buttonText
                  )}
                </button>

                {/* Savings for yearly */}
                {plan.id !== "free" && (
                  <p className="text-center text-xs text-gray-500 mt-2">
                    Save 20% with yearly billing
                  </p>
                )}
              </div>
            );
          })}
        </div>

        {/* Additional Info */}
        <div className="mt-16 text-center">
          <h3 className="text-2xl font-bold mb-4">Frequently Asked Questions</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 max-w-4xl mx-auto text-left">
            <div>
              <h4 className="font-semibold mb-2">Can I cancel anytime?</h4>
              <p className="text-gray-400 text-sm">Yes, you can cancel your subscription at any time. No questions asked.</p>
            </div>
            <div>
              <h4 className="font-semibold mb-2">Is there a free trial?</h4>
              <p className="text-gray-400 text-sm">Yes! All paid plans come with a 7-day free trial.</p>
            </div>
            <div>
              <h4 className="font-semibold mb-2">What payment methods do you accept?</h4>
              <p className="text-gray-400 text-sm">We accept all major credit cards, PayPal, and bank transfers.</p>
            </div>
            <div>
              <h4 className="font-semibold mb-2">Can I change plans?</h4>
              <p className="text-gray-400 text-sm">Yes, you can upgrade or downgrade your plan at any time.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SubscriptionPlans;
