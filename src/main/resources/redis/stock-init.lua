-- 초기화: DB에서 읽은 남은 재고·1인 제한·회원별 구매 수량을 Redis에 올린다.
-- 이미 있으면(다른 요청이 먼저 초기화) 덮어쓰지 않는다.
-- KEYS[1] = 상품 정보 해시, KEYS[2] = 회원별 구매 수량 해시
-- ARGV[1] = 남은 재고, ARGV[2] = 1인 제한, ARGV[3] = 상품명, ARGV[4..] = memberId, quantity 쌍
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end
redis.call('DEL', KEYS[2])
for i = 4, #ARGV, 2 do
    redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1])
end
redis.call('HSET', KEYS[1], 'stock', ARGV[1], 'limit', ARGV[2], 'name', ARGV[3])
return 1
