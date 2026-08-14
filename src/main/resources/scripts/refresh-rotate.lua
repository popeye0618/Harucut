-- KEYS[1] 세션 키          REFRESH_TOKEN:USER:{publicId}
-- KEYS[2] grace 키         REFRESH_GRACE:{제시된 토큰}
-- ARGV[1] 제시된 토큰
-- ARGV[2] 새로 만든 토큰
-- ARGV[3] 세션 TTL (초)
-- ARGV[4] grace TTL (초)

local current = redis.call('GET', KEYS[1])

if not current then
    return {'NO_SESSION', ''}
end

if current == ARGV[1] then
    redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
    redis.call('SET', KEYS[2], '1', 'EX', ARGV[4])
    return {'ROTATED', ARGV[2]}
end

if redis.call('EXISTS', KEYS[2]) == 1 then
    return {'GRACE', current}
end

redis.call('DEL', KEYS[1])
return {'REUSE', ''}
