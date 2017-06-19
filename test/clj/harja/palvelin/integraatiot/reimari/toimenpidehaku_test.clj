(ns harja.palvelin.integraatiot.reimari.toimenpidehaku-test
  (:require [harja.palvelin.integraatiot.reimari.toimenpidehaku :as tohaku]
            [harja.palvelin.integraatiot.reimari.reimari-komponentti :as reimari]
            [harja.domain.vesivaylat.toimenpide :as toimenpide]
            [com.stuartsierra.component :as component]
            [specql.core :as specql]
            [harja.testi :as ht]
            [clojure.test :as t]))

(def jarjestelma-fixture
  (ht/laajenna-integraatiojarjestelmafixturea
   "yit"
   :reimari (component/using
           (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana" nil nil nil)
           [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))

(def referenssi-toimenpide-tietue
  {::toimenpide/urakka-id (ht/hae-helsingin-vesivaylaurakan-id)
   ::toimenpide/suoritettu #inst "2017-04-24T09:42:04.123-00:00",
   ::toimenpide/reimari-id -123456,
   ::toimenpide/reimari-tila "1022541202",
   ::toimenpide/lisatyo? false,
   ::toimenpide/reimari-toimenpidetyyppi "1022542001"
   ::toimenpide/reimari-vayla
   {:harja.domain.vesivaylat.vayla/r-nro "12345",
    :harja.domain.vesivaylat.vayla/r-nimi "Joku väylä"},
    ::toimenpide/id 8,
   ::toimenpide/reimari-luotu
   #inst "2017-04-24T13:00:00.123-00:00",
   ::toimenpide/lisatieto "vaihdettiin patterit lamppuun",
   ::toimenpide/reimari-tyoluokka "1022541905",
   ::toimenpide/reimari-tyolaji "1022541802",
   ::toimenpide/reimari-urakoitsija
   {:harja.domain.vesivaylat.urakoitsija/id 2,
    :harja.domain.vesivaylat.urakoitsija/nimi "Merimiehet Oy"},
   ::toimenpide/reimari-sopimus
   {:harja.domain.vesivaylat.sopimus/r-nro -666,
    :harja.domain.vesivaylat.sopimus/r-tyyppi "1022542301",
    :harja.domain.vesivaylat.sopimus/r-nimi "Hoitosopimus"},
   ::toimenpide/reimari-tyyppi "1022542001",
   ::toimenpide/reimari-muokattu
   #inst "2017-04-24T13:30:00.123-00:00",
   ::toimenpide/reimari-komponentit
   [{:harja.domain.vesivaylat.komponentti/tila "234",
     :harja.domain.vesivaylat.komponentti/nimi "Erikoispoiju",
     :harja.domain.vesivaylat.komponentti/id 123}
    {:harja.domain.vesivaylat.komponentti/tila "345",
     :harja.domain.vesivaylat.komponentti/nimi "Erikoismerkki",
     :harja.domain.vesivaylat.komponentti/id 124}],
   ::toimenpide/reimari-alus
   {:harja.domain.vesivaylat.alus/r-tunnus "omapaatti",
    :harja.domain.vesivaylat.alus/r-nimi "MS Totally out of Gravitas"},
   ::toimenpide/reimari-turvalaite
   {:harja.domain.vesivaylat.turvalaite/r-nro "904",
     :harja.domain.vesivaylat.turvalaite/r-nimi
    "Glosholmsklacken pohjoinen",
    :harja.domain.vesivaylat.turvalaite/r-ryhma 514}})

(t/deftest kasittele-vastaus-kantatallennus
  (let [tarkista-fn  #(ht/tarkista-map-arvot
                       (first (tohaku/kasittele-vastaus
                               (:db ht/jarjestelma)
                               (slurp "resources/xsd/reimari/haetoimenpiteet-vastaus.xml")))
                       referenssi-toimenpide-tietue)]


    (tarkista-fn)

    ;; tarkistetaan että sama reimari-id luetussa toimenpiteessä päivittää tietuetta
    (specql/update! (:db ht/jarjestelma) ::toimenpide/reimari-toimenpide {::toimenpide/lisatyo? true} {::toimenpide/id 8})
    (tarkista-fn)))
