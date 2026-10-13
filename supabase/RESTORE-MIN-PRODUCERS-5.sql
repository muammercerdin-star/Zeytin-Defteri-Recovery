-- Work Order 08.1. Trusted operator only. No deployment hook.
-- Stop concurrent function deployments/DDL during verification and restore.
-- Backup must be the trusted original patch history, never client-writable.
-- On any error ROLLBACK the aborted transaction; never continue individual statements.
begin;
DO $check$
DECLARE
    -- If multiple historical batches exist, copy the reviewed batch_id here
    -- in BOTH files. Never silently select the newest batch.
    selected_batch text := '';
    apply_changes CONSTANT boolean := true;
    r record;
    v_batch text;
    v_batches integer;
    v_def text;
    v_new text;
    v_current text;
    v_oid oid;
    v_status text;
    v_unsafe integer := 0;
    v_eligible integer := 0;
BEGIN
    IF to_regclass('private.market_threshold_patch_backup') IS NULL THEN
        IF apply_changes THEN RAISE EXCEPTION 'Missing original threshold backup; no change applied.'; END IF;
        RAISE NOTICE 'UNSAFE: backup table missing; no restore eligible'; RETURN;
    END IF;
    IF apply_changes THEN
        LOCK TABLE private.market_threshold_patch_backup IN SHARE MODE;
    END IF;
    SELECT count(DISTINCT batch_id), min(batch_id) INTO v_batches,v_batch
    FROM private.market_threshold_patch_backup WHERE from_threshold=5 AND to_threshold=2;
    IF selected_batch <> '' THEN v_batch := selected_batch;
    ELSIF v_batches <> 1 THEN
        IF apply_changes THEN RAISE EXCEPTION 'No original or ambiguous 5-to-2 batch; select an explicitly reviewed batch.'; END IF;
        RAISE NOTICE 'UNSAFE: % batches; choose a reviewed batch_id',v_batches;
        FOR r IN SELECT batch_id,count(*) AS entries FROM private.market_threshold_patch_backup
            WHERE from_threshold=5 AND to_threshold=2 GROUP BY batch_id ORDER BY batch_id
        LOOP RAISE NOTICE 'BATCH %: % entries',r.batch_id,r.entries; END LOOP;
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM private.market_threshold_patch_backup WHERE batch_id=v_batch) THEN
        IF apply_changes THEN RAISE EXCEPTION 'Selected batch missing'; END IF;
        RAISE NOTICE 'UNSAFE: selected batch missing'; RETURN;
    END IF;
    RAISE NOTICE 'BATCH %; apply=%',v_batch,apply_changes;
    -- Name patterns are ONLY an inventory aid, never restore authority.
    FOR r IN SELECT n.nspname,p.proname,pg_get_function_identity_arguments(p.oid) AS args
        FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname IN ('public','private') AND p.prokind='f'
          AND p.proname ~ '^(market_|zd_market_)'
          AND NOT EXISTS (SELECT 1 FROM private.market_threshold_patch_backup b
            WHERE b.batch_id=v_batch AND b.schema_name=n.nspname AND b.function_name=p.proname
              AND b.identity_args=pg_get_function_identity_arguments(p.oid))
    LOOP RAISE NOTICE 'SKIPPED_NO_BACKUP %.%(%)',r.nspname,r.proname,r.args; END LOOP;

    -- ALL entries in this batch, including League. Validate the ENTIRE batch
    -- before the first CREATE OR REPLACE; any unsafe entry aborts apply.
    FOR r IN SELECT b.*, count(*) OVER (PARTITION BY schema_name,function_name,identity_args) AS copies
        FROM private.market_threshold_patch_backup b WHERE batch_id=v_batch
        ORDER BY schema_name,function_name,identity_args
    LOOP
        v_oid := NULL; v_current := NULL;
        SELECT p.oid,pg_get_functiondef(p.oid) INTO v_oid,v_current
        FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname=r.schema_name AND p.proname=r.function_name AND p.prokind='f'
          AND pg_get_function_identity_arguments(p.oid)=r.identity_args;
        v_def := r.function_definition;
        v_new := v_def;
        -- Reconstruct historical test text for comparison ONLY. v_new is NEVER executed.
        -- count(distinct ...producer...) >= 5 / < 5 vb.
        v_new := regexp_replace(
            v_new,
            E'(count[[:space:]]*\\([[:space:]]*distinct[[:space:]]+[^)]*producer[^)]*\\)[[:space:]]*(>=|<=|=|<|>)[[:space:]]*)5([^0-9]|$)',
            E'\\1' || '2' || E'\\3',
            'gi'
        );

        -- producer_count, v_producers, accepted_producers vb. doğrudan karşılaştırmalar.
        v_new := regexp_replace(
            v_new,
            E'([A-Za-z_][A-Za-z0-9_]*producer[A-Za-z0-9_]*[[:space:]]*(>=|<=|=|<|>)[[:space:]]*)5([^0-9]|$)',
            E'\\1' || '2' || E'\\3',
            'gi'
        );

        -- MIN_PRODUCERS / required_producers gibi sabit tanımları.
        v_new := regexp_replace(
            v_new,
            E'([A-Za-z_][A-Za-z0-9_]*(min|required|minimum)[A-Za-z0-9_]*producer[A-Za-z0-9_]*[^;\\n]*(:=|=)[[:space:]]*)5([^0-9]|$)',
            E'\\1' || '2' || E'\\4',
            'gi'
        );



        IF r.copies <> 1 OR r.from_threshold IS DISTINCT FROM 5 OR r.to_threshold IS DISTINCT FROM 2
           OR r.schema_name NOT IN ('public','private') OR r.schema_name IS NULL
           OR v_def IS NULL OR r.identity_args IS NULL OR r.function_name IS NULL THEN
            v_status := 'UNSAFE_METADATA';
        ELSIF v_oid IS NULL THEN v_status := 'UNSAFE_SIGNATURE_OR_MISSING_FUNCTION';
        ELSIF v_new = v_def THEN v_status := 'UNSAFE_UNRECOGNIZED_PATCH';
        ELSIF v_current = v_def THEN v_status := 'SKIPPED_ALREADY_ORIGINAL';
        ELSIF v_current = v_new THEN v_status := 'RESTORE_ELIGIBLE'; v_eligible := v_eligible+1;
        ELSE v_status := 'MISMATCH_NEWER_OR_UNKNOWN_BODY';
        END IF;
        RAISE NOTICE '% %.%(%) backup=yes',v_status,r.schema_name,r.function_name,r.identity_args;
        IF v_status NOT IN ('RESTORE_ELIGIBLE','SKIPPED_ALREADY_ORIGINAL') THEN v_unsafe:=v_unsafe+1; END IF;
    END LOOP;
    RAISE NOTICE 'SUMMARY eligible=% unsafe=%',v_eligible,v_unsafe;
    IF NOT apply_changes THEN RETURN; END IF;
    IF v_unsafe > 0 THEN RAISE EXCEPTION 'Unsafe batch: refusing to overwrite any function (% unsafe entries)',v_unsafe; END IF;

    FOR r IN SELECT * FROM private.market_threshold_patch_backup WHERE batch_id=v_batch
        ORDER BY schema_name,function_name,identity_args
    LOOP
        v_def := r.function_definition; v_new := v_def;
        -- Reconstruct historical test text for comparison ONLY. v_new is NEVER executed.
        -- count(distinct ...producer...) >= 5 / < 5 vb.
        v_new := regexp_replace(
            v_new,
            E'(count[[:space:]]*\\([[:space:]]*distinct[[:space:]]+[^)]*producer[^)]*\\)[[:space:]]*(>=|<=|=|<|>)[[:space:]]*)5([^0-9]|$)',
            E'\\1' || '2' || E'\\3',
            'gi'
        );

        -- producer_count, v_producers, accepted_producers vb. doğrudan karşılaştırmalar.
        v_new := regexp_replace(
            v_new,
            E'([A-Za-z_][A-Za-z0-9_]*producer[A-Za-z0-9_]*[[:space:]]*(>=|<=|=|<|>)[[:space:]]*)5([^0-9]|$)',
            E'\\1' || '2' || E'\\3',
            'gi'
        );

        -- MIN_PRODUCERS / required_producers gibi sabit tanımları.
        v_new := regexp_replace(
            v_new,
            E'([A-Za-z_][A-Za-z0-9_]*(min|required|minimum)[A-Za-z0-9_]*producer[A-Za-z0-9_]*[^;\\n]*(:=|=)[[:space:]]*)5([^0-9]|$)',
            E'\\1' || '2' || E'\\4',
            'gi'
        );



        v_current := NULL;
        SELECT pg_get_functiondef(p.oid) INTO v_current FROM pg_proc p
        JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname=r.schema_name AND p.proname=r.function_name AND p.prokind='f'
          AND pg_get_function_identity_arguments(p.oid)=r.identity_args;
        IF v_current = v_def THEN CONTINUE; END IF;
        IF v_current IS DISTINCT FROM v_new THEN RAISE EXCEPTION 'Concurrent definition change; abort entire restore'; END IF;
        -- Exact comparison includes CREATE header/signature, not merely the SQL body.
        EXECUTE v_def;
    END LOOP;
    RAISE NOTICE 'Restored % verified original definitions; no user data changed',v_eligible;
END
$check$;
commit;
