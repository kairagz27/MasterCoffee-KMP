DO $$ BEGIN
 IF (SELECT last_value + CASE WHEN is_called THEN 1 ELSE 0 END FROM order_number_seq) <= (SELECT coalesce(max(sequence),0) FROM orders)
 OR (SELECT last_value + CASE WHEN is_called THEN 1 ELSE 0 END FROM order_events_id_seq) <= (SELECT coalesce(max(id),0) FROM order_events)
 OR (SELECT last_value + CASE WHEN is_called THEN 1 ELSE 0 END FROM loyalty_ledger_id_seq) <= (SELECT coalesce(max(id),0) FROM loyalty_ledger)
 THEN RAISE EXCEPTION 'Restored sequences precede existing rows'; END IF;
END $$;
