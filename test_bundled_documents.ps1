# PowerShell script to test the bundled document classification and verification

# Set the job number to test
$JOB_NO = "J069026"

Write-Host "Testing bundled document classification for Job No: $JOB_NO" -ForegroundColor Yellow

# Step 1: Check if the job is eligible for verification
Write-Host "`nStep 1: Checking job eligibility..." -ForegroundColor Yellow
$ELIGIBILITY_RESPONSE = Invoke-RestMethod -Uri "http://localhost:8081/api/verification/check-eligibility/$JOB_NO" -Method Get
Write-Host "Eligibility Response: $($ELIGIBILITY_RESPONSE | ConvertTo-Json -Depth 10)" -ForegroundColor Green

# Check if the job is eligible
if (-not $ELIGIBILITY_RESPONSE.isEligible) {
    Write-Host "Job is not eligible for verification. Exiting." -ForegroundColor Red
    exit 1
}

# Step 2: Trigger verification
Write-Host "`nStep 2: Triggering verification..." -ForegroundColor Yellow
$VERIFICATION_RESPONSE = Invoke-RestMethod -Uri "http://localhost:8081/api/verification/trigger/$JOB_NO" -Method Post
Write-Host "Verification Response: $($VERIFICATION_RESPONSE | ConvertTo-Json -Depth 10)" -ForegroundColor Green

# Extract verification request ID
$REQUEST_ID = $VERIFICATION_RESPONSE.id
if (-not $REQUEST_ID) {
    Write-Host "Failed to extract verification request ID. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host "Verification Request ID: $REQUEST_ID" -ForegroundColor Green

# Step 3: Wait for verification to complete
Write-Host "`nStep 3: Waiting for verification to complete..." -ForegroundColor Yellow
$MAX_ATTEMPTS = 30
$ATTEMPT = 0
$STATUS = "PENDING"

while ($STATUS -eq "PENDING" -or $STATUS -eq "PROCESSING") {
    $ATTEMPT++
    if ($ATTEMPT -gt $MAX_ATTEMPTS) {
        Write-Host "Verification timed out after $MAX_ATTEMPTS attempts. Exiting." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Checking verification status (attempt $ATTEMPT/$MAX_ATTEMPTS)..."
    $STATUS_RESPONSE = Invoke-RestMethod -Uri "http://localhost:8081/api/verification/status/$REQUEST_ID" -Method Get
    $STATUS = $STATUS_RESPONSE.status
    
    Write-Host "Current status: $STATUS" -ForegroundColor Yellow
    
    if ($STATUS -eq "PENDING" -or $STATUS -eq "PROCESSING") {
        Write-Host "Waiting 5 seconds before checking again..."
        Start-Sleep -Seconds 5
    }
}

# Step 4: Check final verification result
Write-Host "`nStep 4: Checking final verification result..." -ForegroundColor Yellow
$FINAL_RESULT = Invoke-RestMethod -Uri "http://localhost:8081/api/verification/status/$REQUEST_ID" -Method Get
Write-Host "Final Result: $($FINAL_RESULT | ConvertTo-Json -Depth 10)" -ForegroundColor Green

# Check if verification was successful
if ($STATUS -eq "COMPLETED") {
    Write-Host "Verification completed successfully!" -ForegroundColor Green
    
    # Check if there were any discrepancies
    if ($FINAL_RESULT.discrepancies -and $FINAL_RESULT.discrepancies.Count -gt 0) {
        Write-Host "Discrepancies found in verification:" -ForegroundColor Yellow
        $FINAL_RESULT.discrepancies | ForEach-Object {
            Write-Host "- $($_ | ConvertTo-Json)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "No discrepancies found. All documents verified successfully!" -ForegroundColor Green
    }
} else {
    Write-Host "Verification failed with status: $STATUS" -ForegroundColor Red
}

Write-Host "`nTest completed." -ForegroundColor Yellow
