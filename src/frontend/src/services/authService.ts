import axios from 'axios';

const API_BASE_URL = '/api';

// Types
export interface LoginRequest {
  username: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  email: string;
  password: string;
  roles?: string[];
}

export interface JwtResponse {
  token: string;
  type: string;
  id: number;
  username: string;
  email: string;
  roles: string[];
}

// Login function
export const login = async (loginRequest: LoginRequest): Promise<JwtResponse> => {
  try {
    const response = await axios.post(`${API_BASE_URL}/auth/signin`, loginRequest);
    
    // Store JWT token in localStorage
    if (response.data.token) {
      localStorage.setItem('token', response.data.token);
      localStorage.setItem('userRole', response.data.roles[0]); // Store the first role
    }
    
    return response.data;
  } catch (error) {
    console.error('Login error:', error);
    throw error;
  }
};

// Register function
export const register = async (signupRequest: SignupRequest): Promise<any> => {
  try {
    const response = await axios.post(`${API_BASE_URL}/auth/signup`, signupRequest);
    return response.data;
  } catch (error) {
    console.error('Registration error:', error);
    throw error;
  }
};

// Logout function
export const logout = (): void => {
  localStorage.removeItem('token');
  localStorage.removeItem('userRole');
};

// Get current user from token
export const getCurrentUser = (): JwtResponse | null => {
  const token = localStorage.getItem('token');
  if (token) {
    // In a real app, you might want to decode the JWT token to get user info
    // For now, we'll just return a basic object with the role
    return {
      token,
      type: 'Bearer',
      id: 0, // We don't have this info without decoding the token
      username: '', // We don't have this info without decoding the token
      email: '', // We don't have this info without decoding the token
      roles: [localStorage.getItem('userRole') || '']
    };
  }
  return null;
};

// Check if user is authenticated
export const isAuthenticated = (): boolean => {
  return localStorage.getItem('token') !== null;
};

// Setup axios interceptor for authentication
export const setupAxiosInterceptors = (): void => {
  axios.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers['Authorization'] = 'Bearer ' + token;
      }
      return config;
    },
    (error) => {
      return Promise.reject(error);
    }
  );
  
  // Add a response interceptor to handle token expiration
  axios.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response && error.response.status === 401) {
        // Token expired or invalid, logout the user
        logout();
        window.location.href = '/login';
      }
      return Promise.reject(error);
    }
  );
};
