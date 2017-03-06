(ns harja.palvelin.integraatiot.api.tarkastukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tarkastukset :as api-tarkastukset]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [cheshire.core :as cheshire]
            [clojure.string :as str])
  (:import (java.util Date)))

(def kayttaja "destia")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet) [:db])
    :api-pistetoteuma (component/using
                        (api-tarkastukset/->Tarkastukset)
                        [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :once jarjestelma-fixture)

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
      (is (= tarkastus ["soratie" "jotain outoa" 3 "soratietarkastus.jpg"]) (str "Tarkastuksen data tallentunut ok " id)))))

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
    (is (= (count tarkastus-idt) tarkastuksia-kannassa) "Kaikki tarkastukset ovat kirjautuneet kantaan oikein")))