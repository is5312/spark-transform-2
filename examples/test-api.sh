#!/bin/bash

# Test script for Spark Transform API

set -e

API_BASE="http://localhost:8084/api/transform"

echo "Testing Spark Transform API..."

# Health check
echo "1. Health check..."
curl -s "$API_BASE/health" | jq .
echo ""

# Submit a sample job
echo "2. Submitting sample job..."
JOB_RESPONSE=$(curl -s -X POST "$API_BASE/submit" \
  -H "Content-Type: application/json" \
  -d @sample-request.json)

echo "$JOB_RESPONSE" | jq .

# Extract job ID
JOB_ID=$(echo "$JOB_RESPONSE" | jq -r '.jobId')
echo "Job ID: $JOB_ID"
echo ""

# Check job status
echo "3. Checking job status..."
sleep 2
curl -s "$API_BASE/status/$JOB_ID" | jq .
echo ""

# List running jobs
echo "4. Listing running jobs..."
curl -s "$API_BASE/jobs" | jq .
echo ""

echo "API test completed!"

