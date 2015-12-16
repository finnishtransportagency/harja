DROP TYPE suoritettavatehtava CASCADE;

CREATE TYPE suoritettavatehtava AS
 ENUM ('auraus ja sohjonpoisto',
       'suolaus',
       'pistehiekoitus',
       'linjahiekoitus',
       'pinnan tasaus',
       'liikennemerkkien puhdistus',
       'lumivallien madaltaminen',
       'sulamisveden haittojen torjunta',
       'tiestotarkastus',
       'kelintarkastus',
       'harjaus',
       'koneellinen niitto',
       'koneellinen vesakonraivaus',
       'sorateiden muokkaushoylays',
       'sorateiden polynsidonta',
       'sorateiden tasaus',
       'sorastus',
       'paallysteiden paikkaus',
       'paallysteiden juotostyot',
       'siltojen puhdistus',
       'l- ja p-alueiden puhdistus',
       'muu',
       'liuossuolaus',
       'aurausviitoitus ja kinostimet',
       'lumensiirto',
       'paannejaan poisto'
       );

CREATE TABLE tyokonehavainto (
  tyokoneid integer NOT NULL,
  jarjestelma character varying(128) NOT NULL,
  organisaatio integer,
  viestitunniste integer NOT NULL,
  lahetysaika timestamp without time zone NOT NULL,
  vastaanotettu timestamp without time zone DEFAULT now(),
  tyokonetyyppi character varying(64) NOT NULL,
  sijainti point NOT NULL,
  urakkaid integer,
  tehtavat suoritettavatehtava[],
  edellinensijainti point,
  suunta real,
  CONSTRAINT tyokonehavainto_pkey PRIMARY KEY (tyokoneid),
  CONSTRAINT tyokonehavainto_organisaatio_fkey FOREIGN KEY (organisaatio) REFERENCES organisaatio (id),
  CONSTRAINT tyokonehavainto_urakkaid_fkey FOREIGN KEY (urakkaid) REFERENCES urakka (id)
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
