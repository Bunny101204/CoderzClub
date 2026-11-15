import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useOrganization } from '../context/OrganizationContext';
import { api, apiErrorMessage } from '../apiClient';
import { runExclusive, useMutationGate } from '../actions/mutationGate.js';

const AuthPage = () => {
  const { branding } = useOrganization();
  const [isLogin, setIsLogin] = useState(true);
  const [resetToken, setResetToken] = useState('');
  const [forgotMode, setForgotMode] = useState(false);
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [loading, setLoading] = useState(false);
  const { login, register, resendVerification } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const confirmingToken = useRef("");
  const authGate = useMutationGate();

  useEffect(() => {
    const verified = searchParams.get('verified');
    const reason = searchParams.get('reason');
    const verificationToken = searchParams.get('verifyEmailToken');
    const incomingResetToken = searchParams.get('resetPasswordToken');

    if (incomingResetToken) {
      setResetToken(incomingResetToken);
      setForgotMode(false);
      setIsLogin(true);
      setInfo('Choose a new password for your account.');
      return;
    }

    if (verificationToken) {
      if (confirmingToken.current === verificationToken) {
        return;
      }
      confirmingToken.current = verificationToken;
      api.auth.confirmEmail(verificationToken)
        .then(() => {
          setInfo('Email verified successfully. Please login.');
          setSearchParams((current) => {
            const next = new URLSearchParams(current);
            next.delete('verifyEmailToken');
            next.set('verified', 'true');
            return next;
          }, { replace: true });
        })
        .catch((err) => {
          setError(apiErrorMessage(err, 'Verification failed'));
        });
      return;
    }

    if (reason === 'session_expired') {
      setInfo('Your session expired. Please sign in again.');
    } else if (verified === 'true') {
      setInfo('Email verified successfully. Please login.');
    } else if (searchParams.get('error')) {
      setError('Sign-in could not be completed. Please try again.');
    }
  }, [searchParams, setSearchParams]);

  const handleInputChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    await runExclusive(authGate, async () => {
    setError('');
    setInfo('');
    setLoading(true);

    try {
      if (resetToken) {
        if (!formData.password || formData.password !== formData.confirmPassword) {
          setError(formData.password ? 'Passwords do not match' : 'Please enter a new password');
          return;
        }
        await api.auth.confirmPasswordReset(resetToken, formData.password);
        setResetToken('');
        setSearchParams((current) => {
          const next = new URLSearchParams(current);
          next.delete('resetPasswordToken');
          return next;
        }, { replace: true });
        setInfo('Password reset successfully. Please sign in.');
        setFormData({ username: '', email: '', password: '', confirmPassword: '' });
        return;
      }

      if (forgotMode) {
        if (!formData.email) {
          setError('Email is required');
          return;
        }
        const response = await api.auth.requestPasswordReset(formData.email);
        setInfo(response.data?.message || 'If an eligible account exists for that email, reset instructions have been sent.');
        return;
      }

      if (!formData.username || !formData.password) {
        setError('Please fill in all required fields');
        return;
      }

      if (!isLogin && formData.password !== formData.confirmPassword) {
        setError('Passwords do not match');
        return;
      }

      if (!isLogin && !formData.email) {
        setError('Email is required for registration');
        return;
      }

      let result;
      if (isLogin) {
        result = await login(formData.username, formData.password);
      } else {
        result = await register(
          formData.username,
          formData.email,
          formData.password
        );
      }
      if (result.success) {
        if (result.token) {
          navigate('/');
        } else {
          setInfo(result.message || 'Registration completed. Please verify your email before logging in.');
        }
      } else {
        setError(result.error || 'Unknown error');
      }
    } catch (error) {
      setError(apiErrorMessage(error, 'An error occurred. Please try again.'));
    } finally {
      setLoading(false);
    }
    });
  };

  const toggleMode = () => {
    setIsLogin(!isLogin);
    setForgotMode(false);
    setResetToken('');
    setError('');
    setInfo('');
    setFormData({
      username: '',
      email: '',
      password: '',
      confirmPassword: ''
    });
  };

  const brandName = branding?.displayName || 'CoderzClub';
  const brandTagline = branding?.landingTagline || 'Practice. Compete. Level up.';

  return (
    <div
      className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-100 via-blue-100 to-slate-100 dark:from-gray-900 dark:via-blue-900 dark:to-gray-900"
      style={{
        backgroundImage: `linear-gradient(135deg, color-mix(in srgb, var(--org-brand-primary, #2563eb) 18%, transparent), transparent 55%)`,
      }}
    >
      <div className="app-surface p-8 rounded-2xl shadow-2xl w-full max-w-md">
        <div className="text-center mb-8">
          {branding?.logoUrl ? (
            <img src={branding.logoUrl} alt="" className="mx-auto mb-3 h-12 w-auto object-contain" />
          ) : null}
          <p className="text-sm font-semibold mb-1" style={{ color: 'var(--org-brand-primary, #2563eb)' }} data-testid="auth-brand-name">
            {brandName}
          </p>
          <h1 className="text-3xl font-bold mb-2">
            {resetToken ? 'Reset Password' : forgotMode ? 'Forgot Password' : isLogin ? 'Welcome Back' : `Join ${brandName}`}
          </h1>
          <p className="app-muted">
            {resetToken
              ? 'Enter and confirm your new password'
              : forgotMode
                ? 'We will email reset instructions when an eligible account exists'
                : isLogin ? brandTagline : 'Create your account'}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          {!resetToken && !forgotMode && (
            <div>
              <label className="block text-sm font-medium mb-2">
                Username
              </label>
              <input
                type="text"
                name="username"
                value={formData.username}
                onChange={handleInputChange}
                className="w-full px-4 py-3 rounded-lg app-input focus:outline-none focus:border-blue-500 transition-colors"
                placeholder="Enter your username"
              />
            </div>
          )}

          {((!isLogin && !resetToken) || forgotMode) && (
            <div>
              <label className="block text-sm font-medium mb-2">
                Email
              </label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleInputChange}
                className="w-full px-4 py-3 rounded-lg app-input focus:outline-none focus:border-blue-500 transition-colors"
                placeholder="Enter your email"
              />
            </div>
          )}

          {!forgotMode && (
            <div>
              <label className="block text-sm font-medium mb-2">
                {resetToken ? 'New Password' : 'Password'}
              </label>
              <input
                type="password"
                name="password"
                value={formData.password}
                onChange={handleInputChange}
                className="w-full px-4 py-3 rounded-lg app-input focus:outline-none focus:border-blue-500 transition-colors"
                placeholder={resetToken ? 'Enter a new password' : 'Enter your password'}
              />
            </div>
          )}

          {(!isLogin || resetToken) && !forgotMode && (
            <div>
              <label className="block text-sm font-medium mb-2">
                Confirm Password
              </label>
              <input
                type="password"
                name="confirmPassword"
                value={formData.confirmPassword}
                onChange={handleInputChange}
                className="w-full px-4 py-3 rounded-lg app-input focus:outline-none focus:border-blue-500 transition-colors"
                placeholder="Confirm your password"
              />
            </div>
          )}

          {error && (
            <div className="bg-red-500/20 border border-red-500/50 text-red-300 px-4 py-3 rounded-lg text-sm">
              {error}
            </div>
          )}
          {info && (
            <div className="bg-emerald-500/20 border border-emerald-500/50 text-emerald-200 px-4 py-3 rounded-lg text-sm">
              {info}
            </div>
          )}
          {isLogin && error && error.toLowerCase().includes('email is not verified') && (
            <div className="text-center">
              <button
                type="button"
                onClick={async () => {
                  await runExclusive(authGate, async () => {
                  setError('');
                  setInfo('');
                  setLoading(true);
                  try {
                    const resendResult = await resendVerification(formData.username);
                    if (resendResult.success) {
                      setInfo(resendResult.message || 'Verification email resent. Please check your inbox.');
                    } else {
                      setError(resendResult.error || 'Unable to resend verification email.');
                    }
                  } catch (resendError) {
                    setError('Unable to resend verification email. Please try again.');
                    console.error('Resend verification error:', resendError);
                  } finally {
                    setLoading(false);
                  }
                  });
                }}
                disabled={loading}
                className="mt-3 inline-flex items-center justify-center px-4 py-2 rounded-lg border border-blue-400 text-blue-200 hover:bg-blue-500/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Resend verification email
              </button>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            aria-busy={loading}
            className="w-full bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white font-semibold py-3 px-4 rounded-lg transition-all duration-200 transform hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? (
              <span className="flex items-center justify-center">
                <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                {resetToken ? 'Saving...' : forgotMode ? 'Sending...' : isLogin ? 'Signing In...' : 'Creating Account...'}
              </span>
            ) : (
              resetToken ? 'Save new password' : forgotMode ? 'Send reset link' : isLogin ? 'Sign In' : 'Create Account'
            )}
          </button>
        </form>

        <div className="mt-6 text-center space-y-2">
          {isLogin && !resetToken && !forgotMode && (
            <p className="app-muted">
              <button
                type="button"
                onClick={() => {
                  setForgotMode(true);
                  setError('');
                  setInfo('');
                }}
                className="text-blue-600 dark:text-blue-400 hover:text-blue-500 font-medium transition-colors"
              >
                Forgot password?
              </button>
            </p>
          )}
          <p className="app-muted">
            {forgotMode || resetToken
              ? 'Remembered your password? '
              : isLogin ? "Don't have an account? " : "Already have an account? "}
            <button
              onClick={() => {
                if (forgotMode || resetToken) {
                  setForgotMode(false);
                  setResetToken('');
                  setError('');
                  setInfo('');
                  setSearchParams((current) => {
                    const next = new URLSearchParams(current);
                    next.delete('resetPasswordToken');
                    return next;
                  }, { replace: true });
                  return;
                }
                toggleMode();
              }}
              className="text-blue-600 dark:text-blue-400 hover:text-blue-500 font-medium transition-colors"
            >
              {forgotMode || resetToken ? 'Sign in' : isLogin ? 'Sign up' : 'Sign in'}
            </button>
          </p>
        </div>
      </div>
    </div>
  );
};

export default AuthPage; 