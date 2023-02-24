ALTER TABLE silta
    ADD COLUMN vastuu_urakka INT REFERENCES urakka(id),
    ADD COLUMN urakkatieto_kasin_muokattu BOOL DEFAULT FALSE;

COMMENT ON column silta.vastuu_urakka IS 'Sillasta vastaava urakka. Näytetään siltatarkastus-sivulla siltana, johon urakan kuuluu tehdä vuosittainen tarkastus';
COMMENT ON column silta.urakat IS 'Lista urakoita, jotka ovat ennen kuuluneet urakalle, ja ovat tehneet siltatarkastuksia. Käytetääm mm. siltatarkastusraportin muodostamiseen ja siltatarkastustiedon tallentamiseen. Ennen käytettiin ainoana tapana merkitä urakka, ja tässä sai olla vain yksi aktiivinen urakka, mutta kävi ilmi, että on tilanteita joissa silta siirtyy aktiiviselta urakalta toiselle, jolloin tuli tarve pitää siltaa useammalla aktiivisella urakalla, mutta koska vain yksi urakka voi olla vastuussa sillasta, luotiin vierelle vastuu_urakka-sarake.';
COMMENT ON column silta.urakkatieto_kasin_muokattu IS 'Silta-aineiston tuonti ei voi päätellä aina täysin sillan oikeaa urakkaa. Tämä sarake on lippu, joka kertoo, että sillan urakoita on muokattu käsin, eikä niitä saa ylikirjoittaa aineiston tuonnissa.';
