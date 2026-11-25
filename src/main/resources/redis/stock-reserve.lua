-- 재고 예약: 1인 제한과 재고를 확인하고, 통과하면 차감한다. Redis가 한 번에 하나씩 실행하므로 원자적이다.
-- KEYS[1] = 상품 정보 해시 (stock, limit, name)
-- KEYS[2] = 회원별 구매 수량 해시 (memberId -> quantity)
-- ARGV[1] = memberId, ARGV[2] = 수량
-- 반환: 1 성공, -1 재고 부족, -2 1인 제한 초과, -3 초기화 안 됨
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -3
end
local quantity = tonumber(ARGV[2])
local limit = tonumber(redis.call('HGET', KEYS[1], 'limit'))
local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
if bought + quantity > limit then
    return -2
end
local stock = tonumber(redis.call('HGET', KEYS[1], 'stock'))
if stock < quantity then
    return -1
end
redis.call('HINCRBY', KEYS[1], 'stock', -quantity)
redis.call('HINCRBY', KEYS[2], ARGV[1], quantity)
return 1
