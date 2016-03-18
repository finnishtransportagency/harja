-- Turpolle uusia vammoihin liittyviä kenttiä
CREATE TYPE turvallisuuspoikkeama_aiheutuneet_vammat AS ENUM (
  'haavat_ja_pinnalliset_vammat',
  'luunmurtumat',
  'sijoiltaan_menot_nyrjahdykset_ja_venahdykset',
  'amputoitumiset_ja_irti_repeamiset',
  'tarahdykset_ja_sisaiset_vammat_ruhjevammat',
  'palovammat_syopymat_ja_paleltumat',
  'myrkytykset_ja_tulehdukset',
  'hukkuminen_ja_tukehtuminen',
  'aanen_ja_varahtelyn_vaikutukset',
  'aarilampotilojen_valon_ja_sateilyn_vaikutukset',
  'sokki',
  'useita_samantasoisia_vammoja',
  'muut',
  'ei_tietoa'
);

ALTER TABLE turvallisuuspoikkeama DROP COLUMN vammat;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vammat turvallisuuspoikkeama_aiheutuneet_vammat[];

CREATE TYPE turvallisuuspoikkeama_vahingoittunut_ruumiinosa AS ENUM (
  'paan_alue',
  'silmat',
  'niska_ja_kaula',
  'selka',
  'vartalo',
  'sormi_kammen',
  'ranne',
  'muu_kasi',
  'nilkka',
  'jalkatera_ja_varvas',
  'muu_jalka',
  'koko_keho',
  'ei_tietoa'
);

ALTER TABLE turvallisuuspoikkeama ADD COLUMN vahingoittuneet_ruumiinosat turvallisuuspoikkeama_vahingoittunut_ruumiinosa[];
ALTER TABLE turvallisuuspoikkeama ADD COLUMN sairauspoissaolo_jatkuu BOOLEAN;