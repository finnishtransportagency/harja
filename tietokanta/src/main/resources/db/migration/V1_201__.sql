-- Kuvaus: Poistetaan työkonehavainnoista sopimus id
ALTER TABLE tyokonehavainto DROP COLUMN sopimusid;


DROP FUNCTION tallenna_tai_paivita_tyokonehavainto(
jarjestelma_ character varying,
organisaationimi_ character varying,
ytunnus_ character varying,
viestitunniste_ integer,
lahetysaika_ timestamp,
tyokoneid_ integer,
tyokonetyyppi_ character varying,
sijainti_ point,
suunta_ real,
urakkaid_ integer,
sopimusid_ integer,
tehtavat_ suoritettavatehtava[]
);

CREATE OR REPLACE FUNCTION tallenna_tai_paivita_tyokonehavainto(
  jarjestelma_ character varying,
  organisaationimi_ character varying,
  ytunnus_ character varying,
  viestitunniste_ integer,
  lahetysaika_ timestamp,
  tyokoneid_ integer,
  tyokonetyyppi_ character varying,
  sijainti_ point,
  suunta_ real,
  urakkaid_ integer,
  tehtavat_ suoritettavatehtava[]
) RETURNS VOID AS
  $$
DECLARE
  organisaatioid integer;
BEGIN
  SELECT id INTO organisaatioid FROM organisaatio WHERE nimi=organisaationimi_ AND ytunnus=ytunnus_;

  -- get advisory lock on tyokone_id
  PERFORM pg_advisory_lock(tyokoneid_);

  -- try update
  UPDATE tyokonehavainto
  	 SET jarjestelma=jarjestelma_,
	     organisaatio=organisaatioid,
	     viestitunniste=viestitunniste_,
	     lahetysaika=lahetysaika_,
	     vastaanotettu=DEFAULT,
	     tyokonetyyppi=tyokonetyyppi_,
	     sijainti=sijainti_,
	     suunta=suunta_,
	     urakkaid=urakkaid_,
	     tehtavat=tehtavat_,
	     edellinensijainti=sijainti
  	 WHERE tyokoneid=tyokoneid_;

  IF NOT FOUND THEN
     -- if fails, insert
     INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika,
     	    	 		  tyokoneid, tyokonetyyppi, sijainti, urakkaid, tehtavat, suunta)
	    VALUES (jarjestelma_, organisaatioid, viestitunniste_, lahetysaika_, tyokoneid_, tyokonetyyppi_, sijainti_,
	    	    urakkaid_, tehtavat_, suunta_);
  END IF;

  -- release advisory lock on tyokone_id
  PERFORM pg_advisory_unlock(tyokoneid_);
END;
$$
LANGUAGE plpgsql;