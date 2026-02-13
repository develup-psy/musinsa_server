-- KEYS[1] = queue key
-- ARGV[1] = score (createdAt epoch millis)
-- ARGV[2] = paymentId (member)
--
-- 수용형 큐 정책:
-- - 요청은 DB에 먼저 영속 저장된다.
-- - Redis는 순번/처리 가속 보조 계층이며, 동일 paymentId 중복 등록은 방지한다.

redis.call('ZADD', KEYS[1], 'NX', ARGV[1], ARGV[2])
local rank = redis.call('ZRANK', KEYS[1], ARGV[2])
local total = redis.call('ZCARD', KEYS[1])

return cjson.encode({status = 'QUEUED', rank = rank, total = total})
