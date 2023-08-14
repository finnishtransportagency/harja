-- Lisätään uudet, työkonerajapinnassa seurattavat tehtävät enumiin:
-- liikenteen varmistaminen kelirikkokohteessa, palteen poisto, palteen poisto kaiteen alta, paallystetyn tien polynsidonta, sohjo-ojien teko

ALTER TYPE suoritettavatehtava ADD VALUE 'liikenteen varmistaminen kelirikkokohteessa' BEFORE 'linjahiekoitus';
ALTER TYPE suoritettavatehtava ADD VALUE 'palteen poisto kaiteen alta' BEFORE 'pinnan tasaus';
ALTER TYPE suoritettavatehtava ADD VALUE 'paallystetyn tien polynsidonta' AFTER 'pistehiekoitus';
ALTER TYPE suoritettavatehtava ADD VALUE 'sohjo-ojien teko' AFTER 'siltojen puhdistus';

