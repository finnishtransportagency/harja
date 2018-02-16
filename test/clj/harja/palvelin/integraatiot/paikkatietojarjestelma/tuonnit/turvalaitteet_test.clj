(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet-test
  (:require [harja.testi :as ht]
            [clojure.test :as t]
            clj-time.core
            [harja.kyselyt.vesivaylat.turvalaitteet :as q-vatu-turvalaite]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet :as turvalaite-tuonti])
  (:import (org.postgis PGgeometry)))

(t/use-fixtures :each (ht/laajenna-integraatiojarjestelmafixturea "jvh"))

(def referenssi-turvalaite-shapefilestä
  {:tlnumero 6666666
   :nimi "Loistava I"
   :sijainti "Nevernever"
   :tyyppi "Sektoriloisto"
   :alatyyppi "KIINTEÄ"
   :tila "POISTETTU"
   :vahv_pvm "20121029000000"
   :toim_tila "Poistettu käytöstä"
   :rak_tieto "Radiomasto"
   :nav_laji "Ei sovellettavissa"
   :valaistu "K"
   :omistaja "Kerttu Kilpikonna"
   :vanhatlnro 1
   :paavayla "6666"
   :vaylat "666"
   :the_geom "POINT(431153.667 7203743.451)"})

(def referenssi-turvalaite-tietokannasta
  {:turvalaitenro "6666666"
   :nimi "Loistava I"
   :sijainti "Nevernever"
   :tyyppi "Sektoriloisto"
   :kiintea true
   :tila "POISTETTU"
   :toimintatila "Poistettu käytöstä"
   :rakenne "Radiomasto"
   :navigointilaji "Ei sovellettavissa"
   :valaistu true
   :omistaja "Kerttu Kilpikonna"
   :turvalaitenro_aiempi 1
   :paavayla "6666"
   :koordinaatit (PGgeometry. "POINT(431153.667 7203743.451)")})

(t/deftest vie-turvalaite-tietokantaan
  (turvalaite-tuonti/vie-turvalaite-entry (:db ht/jarjestelma) referenssi-turvalaite-shapefilestä)
  (let [tallentunut-turvalaite (first (q-vatu-turvalaite/hae-turvalaite-tunnuksella (:db ht/jarjestelma) {:turvalaitenro "6666666"}))]
      (ht/tarkista-map-arvot referenssi-turvalaite-tietokannasta tallentunut-turvalaite)))
