-- KEYS: inventory stock keys (inventory:stock:option:{id})
-- ARGV: quantities to increase (same order as KEYS)
-- return: 1 success, -2 invalid argument

for i = 1, #KEYS do
  local qty = tonumber(ARGV[i])
  if not qty or qty <= 0 then
    return -2
  end
end

for i = 1, #KEYS do
  redis.call('INCRBY', KEYS[i], ARGV[i])
end

return 1
