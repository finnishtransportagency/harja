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

  LOOP
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
    IF FOUND THEN
       RETURN;
    END IF;

    BEGIN
     INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika,
     	    	 		  tyokoneid, tyokonetyyppi, sijainti, urakkaid, tehtavat, suunta)
	    VALUES (jarjestelma_, organisaatioid, viestitunniste_, lahetysaika_, tyokoneid_, tyokonetyyppi_, sijainti_,
	    	   urakkaid_, tehtavat_, suunta_);
	RETURN;
    EXCEPTION WHEN unique_violation THEN
      -- retry
    END;
  END LOOP;
END;
$$
LANGUAGE plpgsql;
