(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet-test
  (:require  [harja.testi :as ht]
             [clojure.test :as t]
             [ harja.domain.vesivaylat.vatu-turvalaite :as turvalaite]
             [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet :as turvalaite-tuonti]))




(def referenssi-turvalaite-shapefilestä
  {:numero 6666666
   :nimi "Keine Liebe"
   :koord "(431153.667 7203743.451)"
   :sijainti "Nevernever"
   :tyyppi "Sektoriloisto"
   :tarkenne "KIINTEÄ"
   :tila "POISTETTU"
   :vah_pvm "2012-10-29"
   :toim_tila "Poistettu käytöstä"
   :rakenne "Radiomasto"
   :nav_laji "Ei sovellettavissa"
   :valaistu "K"
   :omistaja "Monica Bellucci"
   :numero_v "1"
   :paavayla 6666
   :vaylat 0 ;[1111 2222 3333 4444 5555] ;;TODO: Tee loppuun
   :the_geom "POINT (431153.667 7203743.451)"})


(def referenssi-turvalaite-tietokannasta
  {:harja.domain.vesivaylat.vatu-turvalaite/turvalaitenro 6666666
   :harja.domain.vesivaylat.vatu-turvalaite/nimi "Keine Liebe"
   :harja.domain.vesivaylat.vatu-turvalaite/koordinaatit "(431153.667 7203743.451)"
   :harja.domain.vesivaylat.vatu-turvalaite/sijainti "Nevernever"
   :harja.domain.vesivaylat.vatu-turvalaite/tyyppi "Sektoriloisto"
   :harja.domain.vesivaylat.vatu-turvalaite/tarkenne "KIINTEÄ"
   :harja.domain.vesivaylat.vatu-turvalaite/tila "POISTETTU"
   :harja.domain.vesivaylat.vatu-turvalaite/vah_pvm "2012-10-29"
   :harja.domain.vesivaylat.vatu-turvalaite/toimintatila "Poistettu käytöstä"
   :harja.domain.vesivaylat.vatu-turvalaite/rakenne "Radiomasto"
   :harja.domain.vesivaylat.vatu-turvalaite/navigointilaji "Ei sovellettavissa"
   :harja.domain.vesivaylat.vatu-turvalaite/valaistu "K"
   :harja.domain.vesivaylat.vatu-turvalaite/omistaja "Monica Bellucci"
   :harja.domain.vesivaylat.vatu-turvalaite/turvalaitenro_aiempi "1"
   :harja.domain.vesivaylat.vatu-turvalaite/paavayla 6666
   :harja.domain.vesivaylat.vatu-turvalaite/vaylat 0 ;[1111 2222 3333 4444 5555]
   :harja.domain.vesivaylat.vatu-turvalaite/geometria "POINT (431153.667 7203743.451)"}
  )

(t/use-fixtures :once ht/tietokanta-fixture)


(t/deftest vie-turvalaite-entry-test
         (let [turvalaite ]
           (turvalaite-tuonti/vie-turvalaite-entry :db turvalaite))
       )
