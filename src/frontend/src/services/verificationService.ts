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

// Define the type for a job pending second check (matching the backend JobListDTO)
export interface JobPendingSecondCheck {
  no: string;    // Primary field (backend now uses @JsonProperty("no"))
  No?: string;   // Fallback for compatibility
  jobNo?: string;
  JobNo?: string;
  firstCheckDate: string;
  secondCheckBy: string;
  description: string;
  billToName: string;
}

/**
 * Fetches jobs pending second check from Business Central.
 * @returns Promise resolving to an array of JobPendingSecondCheck
 */
export const fetchJobsPendingSecondCheck = async (): Promise<JobPendingSecondCheck[]> => {
  try {
    const response = await axios.get<JobPendingSecondCheck[]>(`${API_BASE_URL}/verifications/jobs-pending-second-check`);
    console.log('Raw response from jobs-pending-second-check:', response);
    console.log('Raw response data:', JSON.stringify(response.data, null, 2));

    // Ensure we have an array of jobs with proper job numbers
    const jobs = response.data.map(job => {
      console.log('Processing individual job:', JSON.stringify(job, null, 2));

      // Check for different possible field names (should be "no" now with JsonAlias)
      let jobNo = job.no || job.No || job.jobNo || job.JobNo;

      if (jobNo === undefined || jobNo === null) {
        console.error('Job has no job number property. Available properties:', Object.keys(job));
        return { ...job, no: 'Unknown' };
      }

      return { ...job, no: String(jobNo).trim() };
    });

    console.log('Processed jobs from backend:', jobs);
    return jobs;
  } catch (error) {
    console.error('Error fetching jobs pending second check:', error);
    // Re-throw the error so the component can handle it
    throw error;
  }
};
