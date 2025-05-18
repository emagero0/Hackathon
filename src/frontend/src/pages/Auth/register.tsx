import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Loader2, FileText, Lock, User, Mail, Eye, EyeOff } from 'lucide-react';
import { FieldError } from 'react-hook-form';
import { register as registerUser } from '../../services/authService';
import { toast } from 'sonner';

interface RegisterFormData {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
}

// Renamed to avoid conflict
export function SourceRegisterPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors }
  } = useForm<RegisterFormData>();

  useEffect(() => {
    const prefersDarkMode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    setIsDarkMode(prefersDarkMode);
    // Add listener for changes
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e: MediaQueryListEvent) => setIsDarkMode(e.matches);
    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, []);

  const onSubmit = async (data: RegisterFormData) => {
    setLoading(true);

    try {
      // Prepare the signup request
      const signupRequest = {
        username: data.username,
        email: data.email,
        password: data.password,
        roles: ['user'] // Default role
      };

      // Call the real registration API
      await registerUser(signupRequest);

      // Show success message
      toast.success('Registration successful! Please login.');

      // Redirect to login page after registration
      navigate('/login');
    } catch (error: any) {
      // Use toast for error messages
      toast.error(error.response?.data?.message || 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const password = watch('password');

  const getErrorMessage = (error?: FieldError): string => {
    return error?.message?.toString() || '';
  };

  return (
    <div className={`min-h-screen w-full flex items-center justify-center ${isDarkMode ? 'bg-black' : 'bg-white'}`}>
      <div className="w-full max-w-md border p-2 rounded-md">
        <div className={`rounded-2xl shadow-xl overflow-hidden ${isDarkMode ? 'bg-black' : 'bg-white'}`}>
          <div className="bg-blue-700 p-2 text-center">
            <div className="flex items-center justify-center space-x-2">
              <FileText className="h-8 w-8 text-white" />
              <span className="text-2xl font-bold text-white">AI Job Solution</span>
            </div>
            <p className="mt-2 text-gray-300">Create your account</p>
          </div>

          <div className="p-6">
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username" className={`flex items-center ${isDarkMode ? 'text-gray-300' : 'text-gray-700'}`}>
                  <User className="mr-2 h-4 w-4" />
                  Username
                </Label>
                <Input
                  id="username"
                  type="text"
                  placeholder="Choose a username"
                  {...register('username', {
                    required: 'Username is required',
                    minLength: {
                      value: 3,
                      message: 'Username must be at least 3 characters'
                    }
                  })}
                  className={`py-5 px-4 border-gray-300 focus:ring-2 focus:ring-blue-500 ${isDarkMode ? 'bg-black border-gray-600 text-white' : 'dark:border-gray-600 dark:bg-black dark:text-white'}`}
                />
                {errors.username && (
                  <p className={`text-sm text-red-600 ${isDarkMode ? 'text-red-400' : 'dark:text-red-400'}`}>
                    {getErrorMessage(errors.username)}
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="email" className={`flex items-center ${isDarkMode ? 'text-gray-300' : 'text-gray-700'}`}>
                  <Mail className="mr-2 h-4 w-4" />
                  Email
                </Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="your@email.com"
                  {...register('email', {
                    required: 'Email is required',
                    pattern: {
                      value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                      message: 'Invalid email address'
                    }
                  })}
                  className={`py-5 px-4 border-gray-300 focus:ring-2 focus:ring-blue-500 ${isDarkMode ? 'bg-black border-gray-600 text-white' : 'dark:border-gray-600 dark:bg-black dark:text-white'}`}
                />
                {errors.email && (
                  <p className={`text-sm text-red-600 ${isDarkMode ? 'text-red-400' : 'dark:text-red-400'}`}>
                    {getErrorMessage(errors.email)}
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
                    {...register('password', {
                      required: 'Password is required',
                      minLength: {
                        value: 8,
                        message: 'Password must be at least 8 characters'
                      }
                    })}
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

              <div className="space-y-2">
                <Label htmlFor="confirmPassword" className={`flex items-center ${isDarkMode ? 'text-gray-300' : 'text-gray-700'}`}>
                  <Lock className="mr-2 h-4 w-4" />
                  Confirm Password
                </Label>
                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type={showConfirmPassword ? "text" : "password"}
                    placeholder="••••••••"
                    {...register('confirmPassword', {
                      required: 'Please confirm your password',
                      validate: value =>
                        value === password || 'Passwords do not match'
                    })}
                    className={`py-5 px-4 pr-10 border-gray-300 focus:ring-2 focus:ring-blue-500 ${isDarkMode ? 'bg-black border-gray-600 text-white' : 'dark:border-gray-600 dark:bg-black dark:text-white'}`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className={`absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 ${isDarkMode ? 'text-gray-400 hover:text-gray-300' : 'dark:text-gray-400 dark:hover:text-gray-300'}`}
                  >
                    {showConfirmPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                {errors.confirmPassword && (
                  <p className={`text-sm text-red-600 ${isDarkMode ? 'text-red-400' : 'dark:text-red-400'}`}>
                    {getErrorMessage(errors.confirmPassword)}
                  </p>
                )}
              </div>

              <Button
                type="submit"
                disabled={loading}
                className={`w-full py-5 font-medium rounded-lg transition-all duration-300 shadow-md hover:shadow-lg mt-4 ${isDarkMode ? 'bg-white text-black' : 'bg-blue-700 text-white'}`}
              >
                {loading ? (
                  <>
                    <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                    Creating account...
                  </>
                ) : (
                  'Register'
                )}
              </Button>
            </form>

            {/* Optional: Keep or remove social login buttons */}
            <div className="relative mt-4">
              <div className="absolute inset-0 flex items-center">
                <span className={`w-full border-t ${isDarkMode ? 'border-gray-600' : 'border-gray-300'}`} />
              </div>
              <div className="relative flex justify-center text-xs uppercase">
                <span className={`bg-${isDarkMode ? 'black' : 'white'} px-2 ${isDarkMode ? 'text-gray-400' : 'text-gray-500'}`}>
                  Or continue with
                </span>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4 mt-2">
              <Button variant="outline" type="button" disabled={loading} className={`${isDarkMode ? 'text-white border-gray-600 bg-transparent' : 'text-black border-gray-300'}`}>
                {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <img src="/google.png" alt="Google" className="mr-2 h-8 w-auto" />}
              </Button>
              <Button variant="outline" type="button" disabled={loading} className={`${isDarkMode ? 'text-white border-gray-600 bg-transparent' : 'text-black border-gray-300'}`}>
                {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <img src="/facebook.png" alt="Facebook" className="mr-2 h-8 w-auto" />}
              </Button>
            </div>
            {/* End Optional Social Login */}
          </div>

          <div className="mt-2 text-center">
            <p className={`text-sm ${isDarkMode ? 'text-gray-400' : 'text-gray-600'}`}>
              Already have an account?{' '}
              <Link
                to="/login"
                className={`font-medium text-blue-600 hover:text-blue-500 hover:underline ${isDarkMode ? 'text-blue-500' : 'dark:text-blue-400'}`}
              >
                Sign in
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default SourceRegisterPage; // Export with potentially renamed component
