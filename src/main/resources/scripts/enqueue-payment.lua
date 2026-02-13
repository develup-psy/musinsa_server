-- KEYS[1] = "payment_queue:event:{eventId}"
-- ARGV[1] = timestamp (score, 선착순 기준)
-- ARGV[2] = paymentId (member)
-- ARGV[3] = max queue size (무한 증가 방지, ex: 10000)

local queueSize = redis.call('ZCARD', KEYS[1])

-- 대기열 최대 크기 초과 시 거부 (시스템 보호)
if tonumber(ARGV[3]) > 0 and queueSize >= tonumber(ARGV[3]) then
    return cjson.encode({status = 'REJECTED', rank = -1, total = queueSize})
end

-- 원자적 등록
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
local rank = redis.call('ZRANK', KEYS[1], ARGV[2])

-- TTL 설정 (대기열 만료 방지 + 정리용, 1시간)
redis.call('EXPIRE', KEYS[1], 3600)

return cjson.encode({status = 'QUEUED', rank = rank, total = queueSize + 1})
