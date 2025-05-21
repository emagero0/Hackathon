#!/bin/bash
# Script to test the Business Central field updates after successful verification

# Set the job number to test
JOB_NO="J069028"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Testing Business Central field updates for Job No: ${JOB_NO}${NC}"

# Step 1: Check if the job is eligible for verification
echo -e "\n${YELLOW}Step 1: Checking job eligibility...${NC}"
ELIGIBILITY_RESPONSE=$(curl -s -X GET "http://localhost:8081/api/verification/check-eligibility/${JOB_NO}")
echo -e "Eligibility Response: ${GREEN}${ELIGIBILITY_RESPONSE}${NC}"

# Check if the job is eligible
IS_ELIGIBLE=$(echo $ELIGIBILITY_RESPONSE | grep -o '"isEligible":true' | wc -l)
if [ $IS_ELIGIBLE -eq 0 ]; then
    echo -e "${RED}Job is not eligible for verification. Exiting.${NC}"
    exit 1
fi

# Step 2: Trigger verification
echo -e "\n${YELLOW}Step 2: Triggering verification...${NC}"
VERIFICATION_RESPONSE=$(curl -s -X POST "http://localhost:8081/api/verification/trigger/${JOB_NO}")
echo -e "Verification Response: ${GREEN}${VERIFICATION_RESPONSE}${NC}"

# Extract verification request ID
REQUEST_ID=$(echo $VERIFICATION_RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
if [ -z "$REQUEST_ID" ]; then
    echo -e "${RED}Failed to extract verification request ID. Exiting.${NC}"
    exit 1
fi

echo -e "Verification Request ID: ${GREEN}${REQUEST_ID}${NC}"

# Step 3: Wait for verification to complete
echo -e "\n${YELLOW}Step 3: Waiting for verification to complete...${NC}"
MAX_ATTEMPTS=30
ATTEMPT=0
STATUS="PENDING"

while [ "$STATUS" == "PENDING" ] || [ "$STATUS" == "PROCESSING" ]; do
    ATTEMPT=$((ATTEMPT+1))
    if [ $ATTEMPT -gt $MAX_ATTEMPTS ]; then
        echo -e "${RED}Verification timed out after ${MAX_ATTEMPTS} attempts. Exiting.${NC}"
        exit 1
    fi
    
    echo -e "Checking verification status (attempt ${ATTEMPT}/${MAX_ATTEMPTS})..."
    STATUS_RESPONSE=$(curl -s -X GET "http://localhost:8081/api/verification/status/${REQUEST_ID}")
    STATUS=$(echo $STATUS_RESPONSE | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    
    echo -e "Current status: ${YELLOW}${STATUS}${NC}"
    
    if [ "$STATUS" == "PENDING" ] || [ "$STATUS" == "PROCESSING" ]; then
        echo -e "Waiting 5 seconds before checking again..."
        sleep 5
    fi
done

# Step 4: Check final verification result
echo -e "\n${YELLOW}Step 4: Checking final verification result...${NC}"
FINAL_RESULT=$(curl -s -X GET "http://localhost:8081/api/verification/status/${REQUEST_ID}")
echo -e "Final Result: ${GREEN}${FINAL_RESULT}${NC}"

# Step 5: Check Business Central fields
echo -e "\n${YELLOW}Step 5: Checking Business Central fields...${NC}"
echo -e "Open the following URL in your browser to check the Business Central fields:"
echo -e "${GREEN}https://bctest.dayliff.com:7048/BC160/ODataV4/Company('KENYA')/Job_Card?$filter=No%20eq%20'${JOB_NO}'${NC}"
echo -e "Verify that the following fields have been updated:"
echo -e "  - _x0032_nd_Check_Date: Current date"
echo -e "  - _x0032_nd_Check_By: 'AI LLM Service'"
echo -e "  - _x0032_nd_Check_Time: Current time"
echo -e "  - Verification_Comment: 'Verified by AI LLM Service - All documents passed verification'"

echo -e "\n${YELLOW}Test completed.${NC}"
