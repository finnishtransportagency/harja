-- MHU2025 ja MHU2026-sanktiolajien puuttuvat sanktiolaji-enumista.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'tyon_tekematta_jattaminen' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'tyon_tekematta_jattaminen';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'asiakirjamerkintojen_paikkansa_pitamattomyys' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'asiakirjamerkintojen_paikkansa_pitamattomyys';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'muu_sopimuksen_vastainen_toiminta' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'muu_sopimuksen_vastainen_toiminta';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'talvisuolan_kokonaiskayton_ylitys' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'talvisuolan_kokonaiskayton_ylitys';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'laskutus_ilman_laskutuskelpoisuutta' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'laskutus_ilman_laskutuskelpoisuutta';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'vastuuhenkilon_tenttipistemaara_alentuminen' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'vastuuhenkilon_tenttipistemaara_alentuminen';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'vastuuhenkilon_vaihto' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'vastuuhenkilon_vaihto';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'laskutus_yli_laskutusrajan' AND enumtypid = 'sanktiolaji'::regtype) THEN
    ALTER TYPE sanktiolaji ADD VALUE 'laskutus_yli_laskutusrajan';
  END IF;
END $$;
