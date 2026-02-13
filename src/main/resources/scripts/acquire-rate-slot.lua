-- KEYS[1] = rate window key
-- ARGV[1] = now millis
-- ARGV[2] = window millis
-- ARGV[3] = limit per window
-- ARGV[4] = unique member id

local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local minScore = now - window

-- Sliding Window Log:
-- 최근 window 구간의 로그만 유지하고, 현재 카운트를 기준으로 허용 여부를 결정한다.
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', minScore)
local current = redis.call('ZCARD', KEYS[1])

if current < limit then
    redis.call('ZADD', KEYS[1], now, ARGV[4])
    redis.call('PEXPIRE', KEYS[1], window * 2)
    return 1
end

return 0
