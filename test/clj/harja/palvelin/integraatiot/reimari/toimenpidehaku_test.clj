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
             (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana")
             [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))




(def referenssi-toimenpide-tietue
  {::toimenpide/suoritettu #inst "2017-04-24T09:42:04.123-00:00",
   ::toimenpide/reimari-id -123456,
   ::toimenpide/reimari-tila "1022541202",
   ::toimenpide/reimari-lisatyo? true,
   ::toimenpide/reimari-toimenpidetyyppi "1022542001"
   ::toimenpide/reimari-vayla
   {:harja.domain.vesivaylat.vayla/r-nro "12345",
    :harja.domain.vesivaylat.vayla/r-nimi "Joku väylä"},
   ::toimenpide/reimari-luotu
   #inst "2017-04-24T13:00:00.123-00:00",
   ::toimenpide/lisatieto "vaihdettiin patterit lamppuun",
   ::toimenpide/reimari-tyoluokka "1022541905",
   ::toimenpide/reimari-tyolaji "1022541802",
   ::toimenpide/reimari-urakoitsija
   {:harja.domain.vesivaylat.urakoitsija/id 2,
    :harja.domain.vesivaylat.urakoitsija/nimi "Merimiehet Oy"},
   ::toimenpide/reimari-sopimus
   {:harja.domain.vesivaylat.sopimus/r-diaarinro "5/5"
    :harja.domain.vesivaylat.sopimus/r-nro -5,
    :harja.domain.vesivaylat.sopimus/r-tyyppi "1022542301",
    :harja.domain.vesivaylat.sopimus/r-nimi "Hoitosopimus"},
   ::toimenpide/reimari-muokattu
   #inst "2017-04-24T13:30:00.123-00:00",
   ::toimenpide/reimari-viat [{:harja.domain.vesivaylat.vika/id 42
                       :harja.domain.vesivaylat.vika/tila "korjattu"}
                      {:harja.domain.vesivaylat.vika/id 43
                       :harja.domain.vesivaylat.vika/tila "aiheeton"}
                      ]
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
    :harja.domain.vesivaylat.turvalaite/r-ryhma 555}
   ::toimenpide/reimari-asiakas "Asko Asiakas"
   ::toimenpide/reimari-vastuuhenkilo "Väiski Vastuullinen"
   ::toimenpide/reimari-henkilo-lkm 2})

(t/deftest kasittele-vastaus-kantatallennus
  (let [db (:db ht/jarjestelma)
        tarkista-fn  #(ht/tarkista-map-arvot
                       referenssi-toimenpide-tietue
                       (first (tohaku/kasittele-toimenpiteet-vastaus db (slurp "resources/xsd/reimari/haetoimenpiteet-vastaus.xml"))))]
    (tarkista-fn)
    (t/is (= (ht/hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
             (::toimenpide/urakka-id (first (specql/fetch db
                                                          ::toimenpide/reimari-toimenpide #{::toimenpide/id ::toimenpide/reimari-id ::toimenpide/urakka-id}
                                                          {::toimenpide/reimari-id -123456})))))

    (t/testing "Haku reimari-id:llä toimii"
      (t/is (= 1
               (count (specql/fetch db ::toimenpide/reimari-toimenpide
                                    #{::toimenpide/reimari-id} {::toimenpide/reimari-id (::toimenpide/reimari-id referenssi-toimenpide-tietue)})))))

    (t/testing "Trigger täytti sopimus-id:n"
      (t/is (= 1
               (count (specql/fetch db ::toimenpide/reimari-toimenpide
                                    #{::toimenpide/reimari-id ::toimenpide/sopimus-id}
                                    {::toimenpide/reimari-id (::toimenpide/reimari-id referenssi-toimenpide-tietue)
                                     ::toimenpide/sopimus-id (ht/hae-helsingin-vesivaylaurakan-paasopimuksen-id)})))))

    (t/testing "Trigger täytti hintatyypin"
      (t/is (= 1
               (count (specql/fetch db ::toimenpide/reimari-toimenpide
                                    #{::toimenpide/reimari-id ::toimenpide/hintatyyppi}
                                    {::toimenpide/reimari-id (::toimenpide/reimari-id referenssi-toimenpide-tietue)
                                     ::toimenpide/hintatyyppi :yksikkohintainen})))))

    (t/testing "Tietueen päivitys reimari-id:llä toimii"
      (t/is (= 1
               (specql/update! (:db ht/jarjestelma) ::toimenpide/reimari-toimenpide
                               {::toimenpide/reimari-lisatyo? false}
                               {::toimenpide/reimari-id (::toimenpide/reimari-id referenssi-toimenpide-tietue)})))
      (tarkista-fn))

    (t/testing "Puutteellisilla sopimustiedoilla ei tallenneta sopimusta mutta ei tule myöskään poikkeusta"
      (let [nimeton-sopimus-xml (-> "resources/xsd/reimari/haetoimenpiteet-vastaus.xml"
                                    slurp
                                    (clojure.string/replace "nimi=\"Hoitosopimus\"" ""))]
        (t/is (empty? (tohaku/kasittele-toimenpiteet-vastaus db nimeton-sopimus-xml)))))))
