import React from 'react';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { 
      hasError: false, 
      error: null,
      errorInfo: null,
      errorCount: 0
    };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    // Log error to console in development
    console.error('ErrorBoundary caught error:', error);
    console.error('Error info:', errorInfo);
    
    this.setState(prevState => ({
      error,
      errorInfo,
      errorCount: prevState.errorCount + 1
    }));

    // Log to server in production
    if (process.env.NODE_ENV === 'production') {
      // You can send error to monitoring service here
      // logErrorToService(error, errorInfo);
    }
  }

  handleReset = () => {
    this.setState({ 
      hasError: false, 
      error: null,
      errorInfo: null
    });
    // Reload the page
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center p-4">
          <div className="max-w-md w-full bg-gray-800 rounded-lg shadow-lg p-8">
            <div className="text-center">
              <div className="text-6xl mb-4">⚠️</div>
              <h1 className="text-3xl font-bold mb-4 text-red-500">Something went wrong</h1>
              
              <p className="text-gray-300 mb-4">
                We're sorry! An unexpected error occurred. Please try again.
              </p>

              {process.env.NODE_ENV === 'development' && this.state.error && (
                <details className="mt-6 text-left bg-gray-900 p-4 rounded mb-4 max-h-64 overflow-auto">
                  <summary className="cursor-pointer font-mono text-sm text-yellow-400 mb-2">
                    Error Details (Dev Only)
                  </summary>
                  <pre className="text-xs text-gray-400 whitespace-pre-wrap break-words">
                    {this.state.error.toString()}
                  </pre>
                  {this.state.errorInfo && (
                    <pre className="text-xs text-gray-400 whitespace-pre-wrap break-words mt-2">
                      {this.state.errorInfo.componentStack}
                    </pre>
                  )}
                </details>
              )}

              <button
                onClick={this.handleReset}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded transition-colors"
              >
                Go Home
              </button>

              {this.state.errorCount > 3 && (
                <p className="mt-4 text-xs text-gray-500">
                  Multiple errors detected. Try clearing your browser cache or refreshing.
                </p>
              )}
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
