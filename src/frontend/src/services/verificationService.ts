import axios from 'axios';

// Define the type for the eligibility check response (matching the backend DTO)
interface EligibilityCheckResponse {
  isEligible: boolean;
  jobNo: string;
  jobTitle?: string;
  customerName?: string;
  message: string;
}

// Define the type for the trigger verification response (matching the backend DTO)
interface TriggerResponse {
  verificationRequestId: string;
}

// Base URL for the API - adjust if necessary
const API_BASE_URL = '/api'; // Assuming proxy is set up or same origin

/**
 * Checks the eligibility of a job for verification.
 * @param jobNo The job number to check.
 * @returns Promise resolving to EligibilityCheckResponse
 */
export const checkJobEligibility = async (jobNo: string): Promise<EligibilityCheckResponse> => {
  try {
    const response = await axios.get<EligibilityCheckResponse>(`${API_BASE_URL}/verifications/check-eligibility/${jobNo}`);
    return response.data;
  } catch (error) {
    console.error(`Error checking eligibility for job ${jobNo}:`, error);
    // Re-throw the error so the component can handle it
    throw error;
  }
};

/**
 * Triggers the verification process for a given job number.
 * @param jobNo The job number to verify.
 * @returns Promise resolving to TriggerResponse
 */
export const triggerVerification = async (jobNo: string): Promise<TriggerResponse> => {
  try {
    const response = await axios.post<TriggerResponse>(`${API_BASE_URL}/verifications`, { jobNo });
    return response.data;
  } catch (error) {
    console.error(`Error triggering verification for job ${jobNo}:`, error);
    // Re-throw the error so the component can handle it
    throw error;
  }
};

// Add other verification-related API calls here if needed (e.g., get status by ID)
