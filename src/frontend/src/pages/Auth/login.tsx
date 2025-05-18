import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Loader2, FileText, Lock, User, Eye, EyeOff } from 'lucide-react';
import { FieldError } from 'react-hook-form';
import { login } from '../../services/authService';
import { toast } from 'sonner';

interface LoginFormData {
  username: string;
  password: string;
}

interface User {
  username: string;
  password: string; // Note: Storing password in state/local storage is generally bad practice for real apps
  role: string;
}

interface LoginPageProps {
  onLogin: (user: User) => void;
}

// Renamed to avoid conflict if needed, assuming current project might have LoginPage
export function SourceLoginPage({ onLogin }: LoginPageProps) {
  const [loading, setLoading] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors }
  } = useForm<LoginFormData>();
  const navigate = useNavigate();

  // No longer need dummy users as we're using the real API

  useEffect(() => {
    const prefersDarkMode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    setIsDarkMode(prefersDarkMode);
    // Add listener for changes
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e: MediaQueryListEvent) => setIsDarkMode(e.matches);
    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, []);

  const onSubmit = async (data: LoginFormData) => {
    setLoading(true);

    try {
      // Call the real login API
      const response = await login(data);

      // Create a user object from the response to match the expected format
      const user: User = {
        username: response.username,
        password: '', // We don't store the password
        role: response.roles[0] // Use the first role
      };

      onLogin(user); // Call the function passed from App.tsx
      navigate('/'); // Navigate to dashboard or home on successful login
      toast.success('Login successful!');
    } catch (error: any) {
      // Use toast for error messages
      toast.error(error.response?.data?.message || 'Invalid username or password');
    } finally {
      setLoading(false);
    }
  };

  const getErrorMessage = (error?: FieldError): string => {
    return error?.message?.toString() || '';
  };

  // Using Tailwind classes from the source project - ensure Tailwind is set up in the target project
  return (
    <div className={`min-h-screen w-full flex items-center justify-center p-2 ${isDarkMode ? 'bg-black' : 'bg-white'}`}>
      <div className="w-full max-w-md border p-2 rounded-md">
        <div className={`rounded-2xl shadow-xl overflow-hidden ${isDarkMode ? 'bg-black' : 'bg-white'}`}>
          <div className="bg-blue-500  p-2 text-center">
            <div className="flex items-center justify-center space-x-2">
              <FileText className="h-8 w-8 text-white" />
              <span className="text-2xl font-bold text-white">AI Job Solution</span>
            </div>
            <p className="mt-2 text-gray-300">Welcome Back</p>
          </div>

          <div className="p-8">
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="username" className={`flex items-center ${isDarkMode ? 'text-gray-300' : 'text-gray-700'}`}>
                  <User className="mr-2 h-4 w-4" />
                  Username
                </Label>
                <Input
                  id="username"
                  type="text"
                  placeholder="Enter your username"
                  {...register('username', { required: 'Username is required' })}
                  className={`py-5 px-4 border-gray-300 focus:ring-2 focus:ring-blue-500 ${isDarkMode ? 'bg-black border-gray-600 text-white' : 'dark:border-gray-600 dark:bg-black dark:text-white'}`}
                />
                {errors.username && (
                  <p className={`text-sm text-red-600 ${isDarkMode ? 'text-red-400' : 'dark:text-red-400'}`}>
                    {getErrorMessage(errors.username)}
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="password" className={`flex items-center ${isDarkMode ? 'text-gray-300' : 'text-gray-700'}`}>
                  <Lock className="mr-2 h-4 w-4" />
                  Password
                </Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder="••••••••"
                    {...register('password', { required: 'Password is required' })}
                    className={`py-5 px-4 pr-10 border-gray-300 focus:ring-2 focus:ring-blue-500 ${isDarkMode ? 'bg-black border-gray-600 text-white' : 'dark:border-gray-600 dark:bg-black dark:text-white'}`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className={`absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 ${isDarkMode ? 'text-gray-400 hover:text-gray-300' : 'dark:text-gray-400 dark:hover:text-gray-300'}`}
                  >
                    {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                {errors.password && (
                  <p className={`text-sm text-red-600 ${isDarkMode ? 'text-red-400' : 'dark:text-red-400'}`}>
                    {getErrorMessage(errors.password)}
                  </p>
                )}
              </div>

              <Button
                type="submit"
                disabled={loading}
                className={`w-full py-5 font-medium rounded-lg transition-all duration-300 shadow-md hover:shadow-lg ${isDarkMode ? 'bg-white text-black' : 'bg-blue-700 text-white'}`}
              >
                {loading ? (
                  <>
                    <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                    Signing in...
                  </>
                ) : (
                  'Sign In'
                )}
              </Button>
            </form>

            {/* Optional: Keep or remove social login buttons */}
            <div className="relative mt-6">
              <div className="absolute inset-0 flex items-center">
                <span className={`w-full border-t ${isDarkMode ? 'border-gray-600' : 'border-gray-300'}`} />
              </div>
              <div className="relative flex justify-center text-xs uppercase">
                <span className={`bg-${isDarkMode ? 'black' : 'white'} px-2 ${isDarkMode ? 'text-gray-400' : 'text-gray-500'}`}>
                  Or continue with
                </span>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4 mt-4">
              <Button variant="outline" type="button" disabled={loading} className={`${isDarkMode ? 'text-white border-gray-600 bg-transparent' : 'text-black border-gray-300'}`}>
                {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <img src="/google.png" alt="Google" className="mr-2 h-8 w-auto" />}
              </Button>
              <Button variant="outline" type="button" disabled={loading} className={`${isDarkMode ? 'text-white border-gray-600 bg-transparent' : 'text-black border-gray-300'}`}>
                {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <img src="/facebook.png" alt="Facebook" className="mr-2 h-8 w-auto" />}
              </Button>
            </div>
            {/* End Optional Social Login */}

            {/* Optional: Keep or remove demo credentials display */}
            <div className="mt-4">
              <div className="relative">
                <div className={`absolute inset-0 flex items-center ${isDarkMode ? 'border-gray-600' : 'border-gray-300'}`}>
                  <div className="w-full border-t"></div>
                </div>
                <div className="relative flex justify-center">
                  <span className={`px-2 ${isDarkMode ? 'bg-black text-gray-300' : 'bg-white text-gray-500'}`}>
                  Demo Accounts Credentials
                  </span>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-1 gap-2">
                <button type="button" className={`text-sm text-left p-2 rounded-lg ${isDarkMode ? 'bg-gray-900 text-white' : 'bg-gray-200 text-black'} transition-colors`}>
                  <span className="text-xs md:pl-14">Admin:</span> admin | #admin001
                </button>
                <button type="button" className={`text-sm text-left p-2 rounded-lg ${isDarkMode ? 'bg-gray-900 text-white' : 'bg-gray-200 text-black'} transition-colors`}>
                  <span className="text-xs md:pl-14">Manager:</span> manager | #manager1
                </button>
              </div>
            </div>
            {/* End Optional Demo Credentials */}
          </div>

          <div className={`px-8 py-2 ${isDarkMode ? 'bg-black' : 'bg-gray-50'} text-center`}>
            <p className={`text-sm ${isDarkMode ? 'text-gray-400' : 'text-gray-600'}`}>
              Don't have an account?{' '}
              <Link
                to="/register"
                className={`font-medium text-blue-600 hover:text-blue-500 hover:underline ${isDarkMode ? 'text-blue-500' : 'dark:text-blue-400'}`}
              >
                Sign up
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default SourceLoginPage; // Export with potentially renamed component
