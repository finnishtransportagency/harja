(ns harja.palvelin.integraatiot.api.tarkastukset-api-test
  (:require [clojure.test :refer [deftest testing is use-fixtures compose-fixtures join-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tarkastukset :as api-tarkastukset]
            [harja.palvelin.palvelut.laadunseuranta.tarkastukset :as tarkastukset-palvelu]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [harja.pvm :as pvm])
  (:import (java.util Date)))

(def kayttaja "destia")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil) [:db])
    :api-pistetoteuma (component/using
                        (api-tarkastukset/->Tarkastukset)
                        [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :each (join-fixtures
                      [tietokanta-fixture
                       urakkatieto-fixture
                       jarjestelma-fixture]))

(defn json-sapluunasta [polku pvm id]
  (-> polku
      slurp
      (.replace "__PVM__" (json-tyokalut/json-pvm pvm))
      (.replace "__ID__" (str id))))

(defn hae-vapaa-tarkastus-ulkoinen-id []
  (let [id (rand-int 10000)
        vastaus (q (str "SELECT * FROM tarkastus t WHERE t.ulkoinen_id = " id ";"))]
    (if (empty? vastaus) id (recur))))

(deftest tallenna-soratietarkastus
  (let [pvm (Date.)
        id (hae-vapaa-tarkastus-ulkoinen-id)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/soratietarkastus"] kayttaja portti
                                         (json-sapluunasta "test/resurssit/api/soratietarkastus.json" pvm id))]

    (is (= 200 (:status vastaus)))
    (let [tarkastus (first (q (str "SELECT t.tyyppi, t.havainnot, stm.kiinteys, l.nimi "
                                   "  FROM tarkastus t "
                                   "       JOIN soratiemittaus stm ON stm.tarkastus=t.id "
                                   "       JOIN tarkastus_liite hl ON t.id = hl.tarkastus "
                                   "       JOIN liite l ON hl.liite = l.id"
                                   " WHERE t.ulkoinen_id = " id
                                   "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))]
      (is (= tarkastus ["soratie" "jotain outoa" 3 "soratietarkastus.jpg"]) (str "Tarkastuksen data tallentunut ok " id)))

    (let [poista-vastaus (api-tyokalut/delete-kutsu
                           ["/api/urakat/" urakka "/tarkastus/soratietarkastus"]
                           kayttaja portti
                           (json-sapluunasta "test/resurssit/api/soratietarkastus-poisto.json" pvm id))]
      (is (-> poista-vastaus :status (= 200))))))

(deftest tallenna-virheellinen-soratietarkastus
  (let [vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/soratietarkastus"] kayttaja portti
                                         (-> "test/resurssit/api/soratietarkastus-virhe.json"
                                             slurp))]
    (is (= 400 (:status vastaus)))
    (is (= "invalidi-json" (some-> vastaus :body json/read-str (get "virheet") first (get "virhe") (get "koodi"))))))

(deftest tallenna-ja-poista-talvihoitotarkastus
  (let [pvm (Date.)
        id (hae-vapaa-tarkastus-ulkoinen-id)
        tarkista-kannasta #(first (q (str "SELECT t.tyyppi, t.havainnot, thm.lumimaara, l.nimi "
                                          "  FROM tarkastus t "
                                          "       JOIN talvihoitomittaus thm ON thm.tarkastus=t.id "
                                          "       JOIN tarkastus_liite hl ON t.id = hl.tarkastus "
                                          "       JOIN liite l ON hl.liite = l.id"
                                          " WHERE t.ulkoinen_id = " id
                                          "   AND t.poistettu IS NOT TRUE"
                                          "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))
        tallenna-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"] kayttaja portti
                                                  (json-sapluunasta "test/resurssit/api/talvihoitotarkastus.json" pvm id))]

    (is (= 200 (:status tallenna-vastaus)))
    (let [tark (tarkista-kannasta)]
      (is (= tark ["talvihoito" "jotain talvisen outoa" 15.00M "talvihoitotarkastus.jpg"]) (str "Tarkastuksen data tallentunut ok " id)))

    (let [poista-vastaus (api-tyokalut/delete-kutsu
                           ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]
                           kayttaja portti
                           (json-sapluunasta "test/resurssit/api/talvihoitotarkastus-poisto.json" pvm id))
          olemattoman-poista-vastaus (api-tyokalut/delete-kutsu
                                       ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]
                                       kayttaja portti
                                       (json-sapluunasta "test/resurssit/api/talvihoitotarkastus-poisto.json" pvm 88888888))
          poista-tark (tarkista-kannasta)]
      (is (-> poista-vastaus :status (= 200)))
      (is (-> olemattoman-poista-vastaus :status (= 200)))
      (is (empty? poista-tark)))))

(deftest tarkasta-tarkastajan-lahettaminen
  (let [tarkastus-idt [6660001 6660002 6660003]
        tarkastustemplate {:tarkastus {:tunniste {:id nil}
                                       :aika "2015-02-02T15:01:00Z"
                                       :alkusijainti {:x 441895 :y 7199136}
                                       :loppusijainti {:x 442131 :y 7198982}
                                       :tarkastaja {:id 123 :etunimi "Tarmo" :sukunimi "Tarkastaja"}
                                       :havainnot "jotain talvisen outoa"}
                           :mittaus {:hoitoluokka 1
                                     :lumimaara 15
                                     :tasaisuus 5
                                     :kitka 3.2
                                     :lampotilaIlma -10
                                     :lampotilaTie -6}}
        tarkastus-tarkastajan-kanssa (-> tarkastustemplate
                                         (assoc-in [:tarkastus :tunniste :id] (first tarkastus-idt)))
        tarkastus-ilman-tarkastajaa (-> tarkastustemplate
                                        (assoc-in [:tarkastus :tunniste :id] (second tarkastus-idt))
                                        (assoc-in [:tarkastus :tarkastaja] nil))
        tarkastus-ilman-vailinasella-tarkastajalla (-> tarkastustemplate
                                                       (assoc-in [:tarkastus :tunniste :id] (nth tarkastus-idt 2))
                                                       (assoc-in [:tarkastus :tarkastaja :etunimi] nil))
        tarkastukset {:otsikko {:lahettaja {:jarjestelma "testi", :organisaatio {:nimi "Destia" :ytunnus "y123435"}}
                                :viestintunniste {:id 3333}
                                :lahetysaika "2017-01-25T08:51:07+02"}
                      :tarkastukset [tarkastus-tarkastajan-kanssa
                                     tarkastus-ilman-tarkastajaa
                                     tarkastus-ilman-vailinasella-tarkastajalla]}
        vastaus (api-tyokalut/post-kutsu
                  ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]
                  kayttaja
                  portti
                  (cheshire/encode tarkastukset))

        tarkastuksia-kannassa (ffirst (q (str "SELECT count(id) FROM tarkastus WHERE ulkoinen_id IN (" (str/join "," tarkastus-idt) ");")))]
    (is (= 200 (:status vastaus)) "Kutsu on onnistunut")
    (is (= (count tarkastus-idt) tarkastuksia-kannassa)
        "Kaikki tarkastukset ovat kirjautuneet kantaan oikein")))

;; Kun tarkastus tallennetaan toisen kerran, liitettä ei saa ladata Harjaan toista kertaa.
;; Tarkastuksen tiedot vain päivitetään
(deftest tallenna-talvihoitotarkastus-kahdesti
  (testing "Tallenna talvihoitotarkastus kahdesti"
    (let [pvm (Date.)
          id (hae-vapaa-tarkastus-ulkoinen-id)
          liitteiden-maara-ennen (first (first (q "select count(id) FROM liite")))
          tarkista-kannasta #(first (q (str "SELECT t.tyyppi, t.havainnot, thm.lumimaara, l.nimi "
                                         "  FROM tarkastus t "
                                         "       JOIN talvihoitomittaus thm ON thm.tarkastus=t.id "
                                         "       JOIN tarkastus_liite hl ON t.id = hl.tarkastus "
                                         "       JOIN liite l ON hl.liite = l.id"
                                         " WHERE t.ulkoinen_id = " id
                                         "   AND t.poistettu IS NOT TRUE"
                                         "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))
          vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"] kayttaja portti
                    (json-sapluunasta "test/resurssit/api/talvihoitotarkastus.json" pvm id))
          vastaus2 (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"] kayttaja portti
                     (json-sapluunasta "test/resurssit/api/talvihoitotarkastus.json" pvm id))
          liitteiden-maara-jalkeen (first (first (q "select count(id) FROM liite")))]

      (is (= 200 (:status vastaus)))
      (is (= 200 (:status vastaus2)))
      (is (= (+ 1 liitteiden-maara-ennen) liitteiden-maara-jalkeen))

      (let [tark (tarkista-kannasta)]
        (is (= tark ["talvihoito" "jotain talvisen outoa" 15.00M "talvihoitotarkastus.jpg"]) (str "Tarkastuksen data tallentunut ok " id)))

      (let [poista-vastaus (api-tyokalut/delete-kutsu
                             ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]
                             kayttaja portti
                             (json-sapluunasta "test/resurssit/api/talvihoitotarkastus-poisto.json" pvm id))
            poista-tark (tarkista-kannasta)]
        (is (-> poista-vastaus :status (= 200)))
        (is (empty? poista-tark))))))

;; Kun tarkastus tallennetaan toisen kerran, samaa liitettä ei saa ladata Harjaan toista kertaa.
;; Erilaisen liitteen voi kuitenkin lisätä. Varmistetaan tässä, että se toimii
(deftest tallenna-talvihoitotarkastus-uudella-liitteellä
  (testing "Tallenna talvihoitotarkastus uudella liitteellä"
    (let [pvm (Date.)
          id (hae-vapaa-tarkastus-ulkoinen-id)
          liitteiden-maara-ennen (first (first (q "select count(id) FROM liite")))
          tarkista-kannasta #(q (str "SELECT t.tyyppi, t.havainnot, thm.lumimaara, l.nimi "
                                  "  FROM tarkastus t "
                                  "       JOIN talvihoitomittaus thm ON thm.tarkastus=t.id "
                                  "       JOIN tarkastus_liite hl ON t.id = hl.tarkastus "
                                  "       JOIN liite l ON hl.liite = l.id"
                                  " WHERE t.ulkoinen_id = " id
                                  "   AND t.poistettu IS NOT TRUE"
                                  "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')"))
          vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"] kayttaja portti
                    (json-sapluunasta "test/resurssit/api/talvihoitotarkastus.json" pvm id))
          vastaus2 (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"] kayttaja portti
                     (json-sapluunasta "test/resurssit/api/talvihoitotarkastus_uudella_liitteella.json" pvm id))
          liitteiden-maara-jalkeen (first (first (q "select count(id) FROM liite")))]

      (is (= 200 (:status vastaus)))
      (is (= 200 (:status vastaus2)))
      (is (= (+ 2 liitteiden-maara-ennen) liitteiden-maara-jalkeen))

      (let [tark (tarkista-kannasta)]
        (is (= tark [["talvihoito" "tiet sulina ojat jäässä" 12.00M "toinen_liite.jpg"]
                     ["talvihoito" "tiet sulina ojat jäässä" 12.00M "talvihoitotarkastus.jpg"]]) (str "Tarkastuksen data tallentunut ok " id)))

      ;; Poista tarkistukset
      (let [poista-vastaus (api-tyokalut/delete-kutsu
                             ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]
                             kayttaja portti
                             (json-sapluunasta "test/resurssit/api/talvihoitotarkastus-poisto.json" pvm id))
            poista-tark (tarkista-kannasta)]
        (is (-> poista-vastaus :status (= 200)))
        (is (empty? poista-tark))))))

(deftest tilaajan-konsulttin-tarkastus-ei-nay-urakoitsijalle
  (let [db (luo-testitietokanta)
        hoitokausi (pvm/paivamaaran-hoitokausi (pvm/nyt))
        hoitokauden-alku (first hoitokausi)
        hoitokauden-loppu (second hoitokausi)
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        _ @oulun-maanteiden-hoitourakan-2019-2024-id
        pvm (Date.)
        ulkoinen-id (hae-vapaa-tarkastus-ulkoinen-id)
        tarkista-kannasta #(first (q (str "SELECT t.tyyppi, t.havainnot, thm.lumimaara, l.nimi "
                                       "  FROM tarkastus t "
                                       "       JOIN talvihoitomittaus thm ON thm.tarkastus=t.id "
                                       "       JOIN tarkastus_liite hl ON t.id = hl.tarkastus "
                                       "       JOIN liite l ON hl.liite = l.id"
                                       " WHERE t.ulkoinen_id = " ulkoinen-id
                                       "   AND t.poistettu IS NOT TRUE"
                                       "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='KariKonsultti')")))
        tallenna-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tarkastus/talvihoitotarkastus"] "KariKonsultti" portti
                           (json-sapluunasta "test/resurssit/api/talvihoitotarkastus-tilaajan-konsultti.json" pvm ulkoinen-id))
        ;; Hae tarkastus urakoitsijalle
        urakkavastaavan-haku (tarkastukset-palvelu/hae-urakan-tarkastukset db (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                    {:urakka-id urakka-id
                     :alkupvm hoitokauden-alku
                     :loppupvm hoitokauden-loppu})
        tilaajan-haku (tarkastukset-palvelu/hae-urakan-tarkastukset db +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :alkupvm hoitokauden-alku
                                :loppupvm hoitokauden-loppu})]

    (is (= 200 (:status tallenna-vastaus)))
    (is (= [] urakkavastaavan-haku) "Urakkavastaava ei löydä tarkastuksia, koska ne on konsultin tekemiä")
    (is (> (count tilaajan-haku) 0) "Tilaaja löytää tarkastukset, koska ne on konsultin tekemiä")
    (let [poista-vastaus (api-tyokalut/delete-kutsu
                           ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]
                           kayttaja portti
                           (json-sapluunasta "test/resurssit/api/talvihoitotarkastus-poisto.json" pvm ulkoinen-id))
          poista-tark (tarkista-kannasta)]
      (is (-> poista-vastaus :status (= 200)))
      (is (empty? poista-tark)))))
