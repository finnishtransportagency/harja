-- Sanktiotyyppi-tauluun tuki vv-urakalle (non-transactional)

ALTER TYPE sanktiolaji ADD VALUE 'vesivayla_sakko';
ALTER TYPE sanktiolaji ADD VALUE 'vesivayla_bonus';
ALTER TYPE sanktiolaji ADD VALUE 'vesivayla_muistutus';