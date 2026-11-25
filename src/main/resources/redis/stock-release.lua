-- 예약 반납: DB 주문 실패(보상) 또는 주문 취소 시 재고와 회원 구매 수량을 되돌린다.
-- 키가 없으면(아직 초기화 전) 아무것도 하지 않는다. 다음 초기화 때 DB 값을 그대로 읽어 오기 때문이다.
-- KEYS[1] = 상품 정보 해시, KEYS[2] = 회원별 구매 수량 해시
-- ARGV[1] = memberId, ARGV[2] = 수량
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end
local quantity = tonumber(ARGV[2])
redis.call('HINCRBY', KEYS[1], 'stock', quantity)
local left = redis.call('HINCRBY', KEYS[2], ARGV[1], -quantity)
if left <= 0 then
    redis.call('HDEL', KEYS[2], ARGV[1])
end
return 1
