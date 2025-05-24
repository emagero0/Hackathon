import axios from 'axios';

const API_BASE_URL = '/api'; // Adjust if your backend is served on a different origin

export const getDashboardStats = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/dashboard/stats`);
    return response.data;
  } catch (error) {
    console.error('Error fetching dashboard stats:', error);
    throw error;
  }
};

export const getJobs = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/jobs`);
    return response.data;
  } catch (error) {
    console.error('Error fetching jobs:', error);
    throw error;
  }
};

export const getJob = async (id: string) => {
  try {
    const response = await axios.get(`${API_BASE_URL}/jobs/${id}`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching job with ID ${id}:`, error);
    throw error;
  }
};

export const submitFeedback = async (feedbackData: any) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/feedback`, feedbackData);
    return response.data;
  } catch (error) {
    console.error('Error submitting feedback:', error);
    throw error;
  }
};

export const getActivityLog = async (page: number = 0, size: number = 20) => {
  try {
    const response = await axios.get(`${API_BASE_URL}/activity-log?page=${page}&size=${size}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching activity log:', error);
    throw error;
  }
};

// Interface for the response from GET /api/jobs/{jobNo}/latest-verification
// Matches LatestVerificationResponseDTO on backend
interface LatestVerificationResponse {
  verificationRequestId: string;
  jobNo: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'; // Matches VerificationStatus enum
  requestTimestamp: string; // ISO date string
  resultTimestamp: string | null; // ISO date string or null
  discrepancies: string[];
}


// Renamed function to request verification
export const requestVerification = async (jobNo: string): Promise<{ verificationRequestId: string }> => {
  try {
    // Backend expects an object with jobNo property
    const response = await axios.post(`${API_BASE_URL}/verifications`, { jobNo }); // Path is correct now
    // Backend returns { verificationRequestId: "..." } and 202 Accepted
    return response.data; // Return the response body containing the ID
  } catch (error) {
    console.error(`Error requesting verification for Job No ${jobNo}:`, error);
    throw error;
  }
};

// New function to fetch the latest verification result for a job
export const getLatestVerificationForJob = async (jobNo: string, timestamp?: number): Promise<LatestVerificationResponse | null> => {
  try {
    // Add cache-busting parameter if timestamp is provided
    const cacheBuster = timestamp ? `?_=${timestamp}` : '';
    const response = await axios.get(`${API_BASE_URL}/verifications/job/${jobNo}/latest${cacheBuster}`);
    return response.data;
  } catch (error: any) { // Use 'any' or 'unknown' and check type
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      console.log(`No verification result found for Job No ${jobNo}.`);
      return null; // Return null if not found
    }
    console.error(`Error fetching latest verification result for Job No ${jobNo}:`, error);
    throw error; // Re-throw other errors
  }
};

// Interface for daily stats DTO from backend
interface DailyVerificationStat {
  date: string; // e.g., "Mon", "Tue" or "YYYY-MM-DD"
  verified: number;
  flagged: number;
  pendingOrError: number;
}

// New function to fetch daily verification stats
export const getDailyVerificationStats = async (): Promise<DailyVerificationStat[]> => {
  try {
    const response = await axios.get(`${API_BASE_URL}/dashboard/daily-stats`);
    return response.data || []; // Return data or empty array
  } catch (error) {
    console.error('Error fetching daily verification stats:', error);
    throw error;
  }
};


export const getActivityLogForJob = async (jobId: string) => {
  try {
    const response = await axios.get(`${API_BASE_URL}/activity-log/job/${jobId}`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching activity log for job ID ${jobId}:`, error);
    throw error;
  }
};
