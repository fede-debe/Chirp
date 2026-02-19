--[[
Email Rate Limiting Script for Redis

Purpose: Implements exponential backoff for email sending attempts to prevent abuse.

Arguments:
  KEYS[1] (rateLimitKey): The key that blocks further attempts when set
  KEYS[2] (attemptCountKey): Tracks the number of failed/rate-limited attempts

Returns:
  {attemptCount, ttl}
  - If rate limited: {-1, seconds_remaining}
  - If allowed: {new_attempt_count, 0}

Backoff Strategy:
  - 1st attempt: 60 seconds (1 minute)
  - 2nd attempt: 300 seconds (5 minutes)
  - 3rd+ attempts: 3600 seconds (1 hour)
--]]

-- Get the keys passed from the application
local rateLimitKey = KEYS[1]
local attemptCountKey = KEYS[2]

-- Check if user is currently rate limited
if redis.call('EXISTS', rateLimitKey) == 1 then
    -- Get remaining time on the rate limit
    local ttl = redis.call('TTL', rateLimitKey)
    -- Return -1 to indicate rate limited, along with seconds remaining
    return { -1, ttl > 0 and ttl or 60 }
end

-- Get the current attempt count (how many times they've been rate limited)
local currentCount = redis.call('GET', attemptCountKey)
-- Convert to number, or default to 0 if nil (first attempt)
currentCount = currentCount and tonumber(currentCount) or 0

-- Increment the attempt counter
local newCount = redis.call('INCR', attemptCountKey)

-- Define backoff durations in seconds: 1min, 5min, 1hour
local backoffSeconds = { 60, 300, 3600 }
-- Calculate which backoff to use (caps at index 3 for 1 hour max)
local backoffIndex = math.min(currentCount, 2) + 1

-- Set the rate limit key with the appropriate backoff duration
redis.call('SETEX', rateLimitKey, backoffSeconds[backoffIndex], '1')

-- Keep the attempt counter for 24 hours (86400 seconds)
-- This resets the backoff progression after a day of no attempts
redis.call('EXPIRE', attemptCountKey, 86400)

-- Return the new attempt count and 0 to indicate success (not rate limited yet)
return { newCount, 0 }