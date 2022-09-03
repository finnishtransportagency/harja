-- Uusi tarkastustyyppi, jotta voidaan erottaa ELYjen Autoreista tulevat tiestötarkastukset tilaajan laadunvalvonnaksi.
ALTER TYPE tarkastustyyppi
    ADD VALUE 'tilaajan laadunvalvonta';