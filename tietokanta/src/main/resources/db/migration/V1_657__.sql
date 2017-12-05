-- Taulu Vatusta AVA:n kautta Harjaan tuoduille turvalaitteille. Korvaa myöhemmässä refaktoroinnissa vv_turvalaite-taulun

CREATE TABLE vatu_turvalaite
(
  turvalaitenro        INTEGER NOT NULL,
  nimi                 VARCHAR,
  koordinaatit         VARCHAR,
  sijainti             VARCHAR,
  tyyppi               VARCHAR, -- (Tuntematon, Merimajakka, Sektoriloisto, Linjamerkki, Suuntaloisto, Apuloisto, Muu merkki, Reunamerkki, Tutkamerkki, Poiju, Viitta, Tunnusmajakka, Kummeli)
  tarkenne             VARCHAR, -- (KELLUVA, KIINTEÄ)
  tila                 VARCHAR, -- (AIHIO, VAHVISTETTU, POISTETTU)
  vah_pvm              DATE, -- 1777-07-07
  toimintatila         VARCHAR, -- (Tuntematon, Jatkuva, Toimii tarvittaessa, Poistettu käytöstä, Rajoitettu toiminta-aika, Väliaikainen)
  rakenne              VARCHAR, -- (Fasadivalo, Torni, Rajamerkki, Rajalinjamerkki, Kanavan reunavalo, Poijuviitta, Jääpoiju, "Reunakummeli", Viittapoiju, Suurviitta, Levykummeli, Helikopteritasanne, Radiomasto, Vesitorni, Savupiippu, Tutkatorni, Kirkontorni, Suurpoiju, Kompassintarkistuspaikka)
  navigointilaji       VARCHAR, -- (Tuntematon, Vasen, Oikea, Pohjois, Etelä, Länsi, Itä, Karimerkki, Turvavesimerkki, Erikoismerkki, Ei sovellettavissa)
  valaistu             BOOLEAN, -- (Vatusta tulee K tai E)
  omistaja             VARCHAR,
  turvalaitenro_aiempi INTEGER,
  paavayla             VARCHAR,
  vaylat               INTEGER [],
  geometria            GEOMETRY,
  luoja                VARCHAR,
  luotu                TIMESTAMP,
  muokkaaja            VARCHAR,
  muokattu             TIMESTAMP
);

CREATE UNIQUE INDEX vatu_turvalaitenro_unique_index
  ON vatu_turvalaite (turvalaitenro);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'turvalaitteet-haku');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'turvalaitteet-muutospaivamaaran-haku');


-- lisätään muokkauskentät myös turvalaiteryhmätauluun
ALTER TABLE reimari_turvalaiteryhma
  ADD COLUMN luoja VARCHAR,
  ADD COLUMN luotu TIMESTAMP,
  ADD COLUMN muokkaaja VARCHAR,
  ADD COLUMN muokattu TIMESTAMP
