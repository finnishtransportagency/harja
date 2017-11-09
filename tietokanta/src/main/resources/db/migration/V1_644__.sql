CREATE TABLE kan_hinta (
    id integer NOT NULL,
    otsikko character varying(1024),
    summa numeric,
    yleiskustannuslisa numeric DEFAULT 0 NOT NULL,
    muokkaaja integer,
    muokattu timestamp without time zone,
    luoja integer NOT NULL,
    luotu timestamp without time zone DEFAULT now() NOT NULL,
    poistettu boolean DEFAULT false NOT NULL,
    poistaja integer,
    ryhma text,
    maara numeric(6,2),
    yksikko character varying(64),
    yksikkohinta numeric(8,2),
    CONSTRAINT maara_positiivinen CHECK ((summa >= (0)::numeric)),
    CONSTRAINT validi_hinta CHECK ((((summa IS NOT NULL) OR ((maara IS NOT NULL) AND (yksikko IS NOT NULL) AND (yksikkohinta IS NOT NULL))) AND (((summa IS NOT NULL) AND (maara IS NULL)) OR ((maara IS NOT NULL) AND (summa IS NULL)))))
);
