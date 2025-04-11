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

export const getActivityLogForJob = async (jobId: string) => {
  try {
    const response = await axios.get(`${API_BASE_URL}/activity-log/job/${jobId}`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching activity log for job ID ${jobId}:`, error);
    throw error;
  }
};
