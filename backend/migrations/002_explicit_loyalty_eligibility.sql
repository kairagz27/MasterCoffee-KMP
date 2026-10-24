-- Preserve operator choices. Backfill only the missing field in known source SKUs.
-- Product 19 is fresh juice; product 12 retains its original tea category policy.
UPDATE products SET document=document || jsonb_build_object('loyalty_eligible',
  COALESCE((document->>'is_drink')::boolean,false)
  AND id IN (1,2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18))
WHERE NOT document ? 'loyalty_eligible';

UPDATE order_items SET snapshot=snapshot || jsonb_build_object('loyalty_eligible',
  COALESCE((snapshot->>'is_drink')::boolean,false)
  AND (snapshot->>'product_id')::integer IN (1,2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18))
WHERE NOT snapshot ? 'loyalty_eligible';

-- Existing unfinished quotes require the customer to review this explicit rule.
UPDATE quotes SET expires_at=least(expires_at,now())
WHERE EXISTS(SELECT 1 FROM jsonb_array_elements(snapshot->'items') AS item
             WHERE NOT item ? 'loyalty_eligible');
