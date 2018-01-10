(ns harja.palvelin.integraatiot.api.tieluvat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.api.tieluvat :as tieluvat]
            [harja.domain.tielupa :as tielupa]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.tielupa :as tielupa-q]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut])
  (:import (org.postgis PGgeometry)))

(def kayttaja "livi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil) [:db])
    :api-tieluvat (component/using (tieluvat/->Tieluvat)
                                   [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :once jarjestelma-fixture)

(deftest hae-ely
  (let [db (luo-testitietokanta)
        uudenmaan-elyn-id (ffirst (q "select id from organisaatio where elynumero = 1;"))]

    (is (= {::tielupa/ely uudenmaan-elyn-id} (tieluvat/hae-ely db "Uusimaa" {})))
    (is (thrown+?
          #(tyokalut/tasmaa-poikkeus
             %
             virheet/+viallinen-kutsu+
             virheet/+tuntematon-ely+
             "Tuntematon ELY Tuntematon")
          (tieluvat/hae-ely db "Tuntematon" {})))))

(deftest hae-sijainnit
  (let [db (luo-testitietokanta)
        tielupa-pistesijainnilla {::tielupa/sijainnit [{:harja.domain.tielupa/tie 20
                                                        :harja.domain.tielupa/aet 1
                                                        :harja.domain.tielupa/aosa 1}]}
        tielupa-pistesijainteineen (tieluvat/hae-tieluvan-sijainnit db tielupa-pistesijainnilla)
        tielupa-sijaintivalilla {::tielupa/sijainnit [{:harja.domain.tielupa/tie 20
                                                       :harja.domain.tielupa/aet 1
                                                       :harja.domain.tielupa/aosa 1
                                                       :losa 1
                                                       :let 300}]}
        tielupa-sijaintivaleineen (tieluvat/hae-tieluvan-sijainnit db tielupa-sijaintivalilla)
        tarkasta-tielupa (fn [ilman-sijainti sijainnin-kanssa]
                           (let [avaimet (fn [tielupa] (mapv #(select-keys % [::tielupa/tie ::tielupa/aosa ::tielupa/aet])
                                                             (::tielupa/sijainnit tielupa)))]
                             (is (= (avaimet ilman-sijainti) (avaimet sijainnin-kanssa))))
                           (is (every? #(not (nil? (::tielupa/geometria %))) (::tielupa/sijainnit sijainnin-kanssa))))]
    (tarkasta-tielupa tielupa-pistesijainnilla tielupa-pistesijainteineen)
    (tarkasta-tielupa tielupa-sijaintivalilla tielupa-sijaintivaleineen)))

(deftest kirjaa-uusi-tielupa
  (let [db (luo-testitietokanta)
        tunniste 3453455
        tielupa-json (.replace (slurp "test/resurssit/api/tieluvan-kirjaus.json") "<TUNNISTE>" (str tunniste))
        odotettu {::tielupa/tienpitoviranomainen-sahkopostiosoite "teijo.tienpitaja@example.com"
                  ::tielupa/kohde-postitoimipaikka "Kiiminki"
                  ::tielupa/liikenneohjaajan-sahkopostiosoite "lilli.liikenteenohjaaja@example.com"
                  ::tielupa/liikenneohjaajan-yhteyshenkilo "Lilli Liikenteenohjaaja"
                  ::tielupa/tienpitoviranomainen-puhelinnumero "987-7889087"
                  ::tielupa/voimassaolon-alkupvm #inst "2020-09-21T21:00:00.000-00:00"
                  ::tielupa/tienpitoviranomainen-yhteyshenkilo "Teijo Tienpitäjä"
                  ::tielupa/kunta "Kiiminki"
                  ::tielupa/kohde-lahiosoite "Tie 123"
                  ::tielupa/liikenneohjaajan-nimi "Liikenneohjaus Oy"
                  ::tielupa/paatoksen-diaarinumero "123456789"
                  ::tielupa/hakija-tyyppi "kotitalous"
                  ::tielupa/urakka 4
                  ::tielupa/kaapeliasennukset []
                  ::tielupa/urakoitsija-sahkopostiosoite "yrjana.yhteyshenkilo@example.com"
                  ::tielupa/hakija-postinumero "90900"
                  ::tielupa/sijainnit
                  [{::tielupa/ajorata 0
                    ::tielupa/tie 20
                    ::tielupa/aosa 6
                    ::tielupa/aet 2631
                    ::tielupa/kaista 1}]
                  ::tielupa/urakoitsija-puhelinnumero "987-7889087"
                  ::tielupa/otsikko "Lupa lisätä mainos tielle"
                  ::tielupa/hakija-postinosoite "Liitintie 1"
                  ::tielupa/urakan-nimi "Oulun alueurakka"
                  ::tielupa/ely 12
                  ::tielupa/kohde-postinumero "90900"
                  ::tielupa/id 3
                  ::tielupa/ulkoinen-tunniste 3453455
                  ::tielupa/saapumispvm #inst "2017-09-21T21:00:00.000-00:00"
                  ::tielupa/liikenneohjaajan-puhelinnumero "987-7889087"
                  ::tielupa/katselmus-url "https://tilu.fi/1234"
                  ::tielupa/voimassaolon-loppupvm #inst "2020-09-21T21:00:00.000-00:00"
                  ::tielupa/hakija-nimi "Henna Hakija"
                  ::tielupa/myontamispvm #inst "2018-09-21T21:00:00.000-00:00"
                  ::tielupa/tyyppi :mainoslupa
                  ::tielupa/hakija-sahkopostiosoite "henna.hakija@example.com"
                  ::tielupa/hakija-puhelinnumero "987-7889087"
                  ::tielupa/tien-nimi "Kuusamontie"
                  ::tielupa/urakoitsija-yhteyshenkilo "Yrjänä Yhteyshenkilo"
                  ::tielupa/urakoitsija-nimi "Puulaaki Oy"}]
    (api-tyokalut/post-kutsu ["/api/tieluvat"] kayttaja portti tielupa-json)
    (let [haettu-tielupa (first (tielupa-q/hae-tieluvat db {::tielupa/ulkoinen-tunniste tunniste}))
          haettu-tielupa (-> haettu-tielupa
                             (dissoc ::muokkaustiedot/luotu)
                             (assoc ::tielupa/sijainnit (map #(dissoc % ::tielupa/geometria) (::tielupa/sijainnit haettu-tielupa))))]
      (is (= odotettu haettu-tielupa)))))


