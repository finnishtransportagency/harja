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
             (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana" nil nil nil nil)
             [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))



(def turvalaiteryhma-tietue
  {:turvalaiteryma/tunnus "1234",
   :turvalaiteryhma/nimi "Merireimari"
   :turvalaiteryhma/kuvaus "1234: Merireimari"
   :turvalaiteryhma/turvalaitteet
   [{:harja.domain.vesivaylat.turvalaite/nro "5678",
     :harja.domain.vesivaylat.turvalaite/nimi "Michelsöharun",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}
    {:harja.domain.vesivaylat.turvalaite/nro "5679",
     :harja.domain.vesivaylat.turvalaite/nimi "Huldviksgrund",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}
    {:harja.domain.vesivaylat.turvalaite/nro "5670",
     :harja.domain.vesivaylat.turvalaite/nimi "Klobben ylempi",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}
    {:harja.domain.vesivaylat.turvalaite/nro "5671",
     :harja.domain.vesivaylat.turvalaite/nimi "Svartholm ylempi",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}]})


(t/deftest kasittele-vastaus-kantatallennus
  (let [db (:db ht/jarjestelma)
        tarkista-fn  #(ht/tarkista-map-arvot
                        turvalaiteryhma-tietue
                       (first (tohaku/kasittele-toimenpiteet-vastaus db (slurp "resources/xsd/reimari/haetoimenpiteet-vastaus.xml"))))]
    (tarkista-fn)
    (t/is (= (ht/hae-helsingin-vesivaylaurakan-id)
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
