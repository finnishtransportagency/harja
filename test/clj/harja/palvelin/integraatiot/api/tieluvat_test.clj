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
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]))

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

(deftest kirjaa-uusi-mainoslupa
  (let [db (luo-testitietokanta)
        tunniste 3453455
        tielupa-json (.replace (slurp "test/resurssit/api/tieluvan-kirjaus-mainoslupa.json") "<TUNNISTE>" (str tunniste))
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
                  ::tielupa/urakat [4]
                  ::tielupa/kaapeliasennukset []
                  ::tielupa/urakoitsija-sahkopostiosoite "yrjana.yhteyshenkilo@example.com"
                  ::tielupa/hakija-postinumero "90900"
                  ::tielupa/sijainnit
                  [{::tielupa/ajorata 0
                    ::tielupa/tie 20
                    ::tielupa/aosa 6
                    ::tielupa/aet 2631
                    ::tielupa/kaista 11}]
                  ::tielupa/urakoitsija-puhelinnumero "987-7889087"
                  ::tielupa/otsikko "Lupa lisätä mainos tielle"
                  ::tielupa/hakija-postinosoite "Liitintie 1"
                  ::tielupa/urakoiden-nimet ["Oulun alueurakka"]
                  ::tielupa/ely 12
                  ::tielupa/kohde-postinumero "90900"
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
      (tarkista-map-arvot odotettu haettu-tielupa))))


(deftest kirjaa-uusi-suojaalue-lupa
  (let [db (luo-testitietokanta)
        tunniste 373773
        tielupa-json (.replace (slurp "test/resurssit/api/tieluvan-kirjaus-suojaalue.json") "<TUNNISTE>" (str tunniste))
        odotettu #:harja.domain.tielupa{:urakoitsija-yhteyshenkilo
                                        "Yrjänä Yhteyshenkilo",
                                        :tienpitoviranomainen-sahkopostiosoite
                                        "teijo.tienpitaja@example.com",
                                        :voimassaolon-alkupvm
                                        #inst "2020-09-21T21:00:00.000-00:00",
                                        :kohde-postitoimipaikka "Kiiminki",
                                        :kohde-lahiosoite "Tie 123",
                                        :liikenneohjaajan-yhteyshenkilo
                                        "Lilli Liikenteenohjaaja",
                                        :hakija-postinumero "90900",
                                        :kunta "Kiiminki",
                                        :liikenneohjaajan-sahkopostiosoite
                                        "lilli.liikenteenohjaaja@example.com",
                                        :urakoitsija-sahkopostiosoite
                                        "yrjana.yhteyshenkilo@example.com",
                                        :tienpitoviranomainen-yhteyshenkilo
                                        "Teijo Tienpitäjä",
                                        :tienpitoviranomainen-puhelinnumero
                                        "987-7889087",
                                        :sijainnit
                                        [#:harja.domain.tielupa {:tie 20,
                                                                 :aosa 6,
                                                                 :aet 2631,
                                                                 :ajorata 0,
                                                                 :kaista 1}],
                                        :hakija-tyyppi "kotitalous",
                                        :kaapeliasennukset [],
                                        :liikenneohjaajan-nimi "Liikenneohjaus Oy",
                                        :paatoksen-diaarinumero "123456789",
                                        :saapumispvm
                                        #inst "2017-09-21T21:00:00.000-00:00",
                                        :otsikko "Lupa rakentaa aitta suoja-alueelle",
                                        :katselmus-url "https://tilu.fi/1234",
                                        :urakoiden-nimet ["Oulun alueurakka"],
                                        :hakija-postinosoite "Liitintie 1",
                                        :urakoitsija-puhelinnumero "987-7889087",
                                        :kohde-postinumero "90900",
                                        :hakija-puhelinnumero "987-7889087",
                                        :ulkoinen-tunniste 373773,
                                        :liikenneohjaajan-puhelinnumero "987-7889087",
                                        :tien-nimi "Kuusamontie",
                                        :hakija-nimi "Henna Hakija",
                                        :myontamispvm
                                        #inst "2018-09-21T21:00:00.000-00:00",
                                        :hakija-sahkopostiosoite
                                        "henna.hakija@example.com",
                                        :tyyppi :suoja-aluerakentamislupa,
                                        :urakoitsija-nimi "Puulaaki Oy",
                                        :voimassaolon-loppupvm
                                        #inst "2020-09-21T21:00:00.000-00:00"}]
    (api-tyokalut/post-kutsu ["/api/tieluvat"] kayttaja portti tielupa-json)
    (let [haettu-tielupa (first (tielupa-q/hae-tieluvat db {::tielupa/ulkoinen-tunniste tunniste}))
          _ (prn haettu-tielupa)
          haettu-tielupa (-> haettu-tielupa
                             (dissoc ::muokkaustiedot/luotu)
                             (assoc ::tielupa/sijainnit (map #(dissoc % ::tielupa/geometria) (::tielupa/sijainnit haettu-tielupa))))]
      (tarkista-map-arvot odotettu haettu-tielupa))))



(deftest kirjaa-uusi-liittymalupalupa
  (let [db (luo-testitietokanta)
        tunniste 43858
        tielupa-json (.replace (slurp "test/resurssit/api/tieluvan-kirjaus-liittymalupa.json") "<TUNNISTE>" (str tunniste))
        odotettu #:harja.domain.tielupa{:urakoitsija-yhteyshenkilo
                                        "Yrjänä Yhteyshenkilo",
                                        :tienpitoviranomainen-sahkopostiosoite
                                        "teijo.tienpitaja@example.com",
                                        :voimassaolon-alkupvm
                                        #inst "2020-09-21T21:00:00.000-00:00",
                                        :kohde-postitoimipaikka "Kiiminki",
                                        :kohde-lahiosoite "Tie 123",
                                        :liikenneohjaajan-yhteyshenkilo
                                        "Lilli Liikenteenohjaaja",
                                        :hakija-postinumero "90900",
                                        :kunta "Kiiminki",
                                        :liikenneohjaajan-sahkopostiosoite
                                        "lilli.liikenteenohjaaja@example.com",
                                        :urakoitsija-sahkopostiosoite
                                        "yrjana.yhteyshenkilo@example.com",
                                        :tienpitoviranomainen-yhteyshenkilo
                                        "Teijo Tienpitäjä",
                                        :tienpitoviranomainen-puhelinnumero
                                        "987-7889087",
                                        :sijainnit
                                        [#:harja.domain.tielupa {:tie 20,
                                                                 :aosa 6,
                                                                 :aet 2631,
                                                                 :ajorata 0,
                                                                 :kaista 11}],
                                        :hakija-tyyppi "kotitalous",
                                        :kaapeliasennukset [],
                                        :liikenneohjaajan-nimi "Liikenneohjaus Oy",
                                        :paatoksen-diaarinumero "123456789",
                                        :saapumispvm
                                        #inst "2017-09-21T21:00:00.000-00:00",
                                        :otsikko
                                        "Lupa rakentaa uusi liittymä mökkitielle",
                                        :katselmus-url "https://tilu.fi/1234",
                                        :urakoiden-nimet ["Oulun alueurakka"],
                                        :hakija-postinosoite "Liitintie 1",
                                        :urakoitsija-puhelinnumero "987-7889087",
                                        :kohde-postinumero "90900",
                                        :hakija-puhelinnumero "987-7889087",
                                        :ulkoinen-tunniste 43858,
                                        :liikenneohjaajan-puhelinnumero "987-7889087",
                                        :tien-nimi "Kuusamontie",
                                        :hakija-nimi "Henna Liittymä",
                                        :myontamispvm
                                        #inst "2018-09-21T21:00:00.000-00:00",
                                        :hakija-sahkopostiosoite
                                        "henna.hakija@example.com",
                                        :tyyppi :liittymalupa,
                                        :urakoitsija-nimi "Puulaaki Oy",
                                        :voimassaolon-loppupvm
                                        #inst "2020-09-21T21:00:00.000-00:00"}
        ]
    (api-tyokalut/post-kutsu ["/api/tieluvat"] kayttaja portti tielupa-json)
    (let [haettu-tielupa (first (tielupa-q/hae-tieluvat db {::tielupa/ulkoinen-tunniste tunniste}))
          _ (prn "haettu-tielpa:" haettu-tielupa)
          haettu-tielupa (-> haettu-tielupa
                             (dissoc ::muokkaustiedot/luotu)
                             (assoc ::tielupa/sijainnit (map #(dissoc % ::tielupa/geometria) (::tielupa/sijainnit haettu-tielupa))))]

      (tarkista-map-arvot odotettu haettu-tielupa))))
