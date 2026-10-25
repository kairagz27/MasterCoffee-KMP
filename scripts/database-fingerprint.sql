SET timezone = 'UTC';
SELECT md5(jsonb_build_object(
  'users', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM users t),
  'sessions', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY token_hash),'[]'::jsonb) FROM sessions t),
  'quotes', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM quotes t),
  'orders', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY sequence),'[]'::jsonb) FROM orders t),
  'items', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY order_id,position),'[]'::jsonb) FROM order_items t),
  'ledger', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM loyalty_ledger t),
  'events', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM order_events t),
  'products', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM products t),
  'availability', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY cafe_id,product_id),'[]'::jsonb) FROM product_availability t),
  'stories', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM stories t),
  'cafes', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM cafes t),
  'addons', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM addons t),
  'migrations', (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY version),'[]'::jsonb) FROM schema_migrations t)
)::text);
