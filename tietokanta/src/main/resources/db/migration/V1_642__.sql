-- Taulu Vatusta AVA:n kautta Harjaan tuoduille turvalaitteille. Korvaa myöhemmässä refaktoroinnissa vv_turvalaite-taulun

CREATE TABLE vatu_turvalaite
(
  id INTEGER PRIMARY KEY NOT NULL,
  turvalaitenro INTEGER,
  nimi VARCHAR,
  sijainti GEOMETRY,
  sijaintikuvaus VARCHAR,
  tyyppi VARCHAR, -- (Tuntematon, Merimajakka, Sektoriloisto, Linjamerkki, Suuntaloisto, Apuloisto, Muu merkki, Reunamerkki, Tutkamerkki, Poiju, Viitta, Tunnusmajakka, Kummeli)
  tarkenne VARCHAR, -- (KELLUVA, KIINTEÄ)
  tila VARCHAR, -- (AIHIO, VAHVISTETTU, POISTETTU)
  vah_pvm DATE, -- 1777-07-07
  toimintatila VARCHAR, -- (Tuntematon, Jatkuva, Toimii tarvittaessa, Poistettu käytöstä, Rajoitettu toiminta-aika, Väliaikainen)
  rakenne VARCHAR, -- (Fasadivalo, Torni, Rajamerkki, Rajalinjamerkki, Kanavan reunavalo, Poijuviitta, Jääpoiju, "Reunakummeli", Viittapoiju, Suurviitta, Levykummeli, Helikopteritasanne, Radiomasto, Vesitorni, Savupiippu, Tutkatorni, Kirkontorni, Suurpoiju, Kompassintarkistuspaikka)
  navigointilaji VARCHAR, -- (Tuntematon, Vasen, Oikea, Pohjois, Etelä, Länsi, Itä, Karimerkki, Turvavesimerkki, Erikoismerkki, Ei sovellettavissa)
  valaistu BOOLEAN, -- (Vatusta K tai E)
  omistaja VARCHAR,
  turvalaitenro_aiempi INTEGER,
  paavayla VARCHAR,
  vaylat INTEGER[]
)

CREATE UNIQUE INDEX vatu_turvalaite_turvalaitenro ON vatu_turvalaite (turvalaitenro);
CREATE INDEX vatu_turvalaitenro_index ON vatu_turvalaite (turvalaitenro);
