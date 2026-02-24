-- KEYS: inventory stock keys (inventory:stock:option:{id})
-- ARGV: quantities to decrease (same order as KEYS)
-- return: 1 success, 0 insufficient, -1 missing key, -2 invalid argument

for i = 1, #KEYS do
  local current = redis.call('GET', KEYS[i])
  if not current then
    return -1
  end

  local qty = tonumber(ARGV[i])
  if not qty or qty <= 0 then
    return -2
  end

  if tonumber(current) < qty then
    return 0
  end
end

for i = 1, #KEYS do
  redis.call('DECRBY', KEYS[i], ARGV[i])
end

return 1
