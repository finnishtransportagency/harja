(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet-test
  (:require  [harja.testi :as ht]
             [clojure.test :as t]
             [harja.domain.vesivaylat.vatu-turvalaite :as turvalaite]
             [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet :as turvalaite-tuonti]))

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
   :vaylat "1"
   :the_geom "POINT (431153.667 7203743.451)"})



(def referenssi-turvalaite-tietokannasta
  {:harja.domain.vesivaylat.vatu-turvalaite/turvalaitenro 6666666
   :harja.domain.vesivaylat.vatu-turvalaite/nimi "Loistava I"
   :harja.domain.vesivaylat.vatu-turvalaite/koordinaatit "POINT (431153.667 7203743.451)"
   :harja.domain.vesivaylat.vatu-turvalaite/sijainti "Nevernever"
   :harja.domain.vesivaylat.vatu-turvalaite/tyyppi "Sektoriloisto"
   :harja.domain.vesivaylat.vatu-turvalaite/tarkenne "KIINTEÄ"
   :harja.domain.vesivaylat.vatu-turvalaite/tila "POISTETTU"
   :harja.domain.vesivaylat.vatu-turvalaite/vah_pvm "2012-10-29"
   :harja.domain.vesivaylat.vatu-turvalaite/toimintatila "Poistettu käytöstä"
   :harja.domain.vesivaylat.vatu-turvalaite/rakenne "Radiomasto"
   :harja.domain.vesivaylat.vatu-turvalaite/navigointilaji "Ei sovellettavissa"
   :harja.domain.vesivaylat.vatu-turvalaite/valaistu "K"
   :harja.domain.vesivaylat.vatu-turvalaite/omistaja "Kerttu Kilpikonna"
   :harja.domain.vesivaylat.vatu-turvalaite/turvalaitenro_aiempi "1"
   :harja.domain.vesivaylat.vatu-turvalaite/paavayla "6666"
   :harja.domain.vesivaylat.vatu-turvalaite/vaylat "1"
   :harja.domain.vesivaylat.vatu-turvalaite/geometria "POINT (431153.667 7203743.451)"}
  )

(t/use-fixtures :once ht/tietokanta-fixture)


(t/deftest vie-turvalaite-entry-test
           (turvalaite-tuonti/vie-turvalaite-entry (:db ht/jarjestelma) referenssi-turvalaite-shapefilestä))
