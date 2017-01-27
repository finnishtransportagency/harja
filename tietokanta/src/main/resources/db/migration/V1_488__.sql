CREATE TYPE laheinen_osoiterivi AS (
 tie INTEGER,      -- TR tie
 osa INTEGER,      -- TR osa
 etaisyys INTEGER, -- TR etäisyys tieosan alusta
 ajorata INTEGER,  -- TR ajorata
 d NUMERIC 	   -- ST_Distance annetusta pisteesta
);