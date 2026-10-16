(ns harja.palvelin.integraatiot.api.liitteet-api-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cheshire.core :as cheshire]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.api.liitteet :as api-liitteet]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.testi :refer :all]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil nil nil) [:db])
    :api-liitteet (component/using
                    (api-liitteet/->Liitteet)
                    [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :once jarjestelma-fixture)

(defn- base64 [sisalto]
  (.encodeToString (java.util.Base64/getEncoder) (.getBytes sisalto "UTF-8")))

(defn- liite-pyynto [kohdeluokka kohde-id liite-tunniste nimi]
  (cheshire/encode
    {:otsikko {:lahettaja {:jarjestelma "liite-api-testi"
                           :organisaatio {:nimi "Harja"
                                          :ytunnus "1234567-8"}}
               :viestintunniste {:id liite-tunniste}
               :lahetysaika "2026-01-01T12:00:00Z"}
     :liite-lahetys {:kohdeluokka kohdeluokka
                     :kohdetunniste {:id kohde-id}
                     :liite {:tunniste {:id liite-tunniste}
                             :nimi nimi
                             :tyyppi "text/plain"
                             :sisalto (base64 "liitteen testisisalto")}}}))

(defn- poisto-pyynto [liite-tunniste]
  (cheshire/encode
    {:otsikko {:lahettaja {:jarjestelma "liite-api-testi"
                           :organisaatio {:nimi "Harja"
                                          :ytunnus "1234567-8"}}
               :viestintunniste {:id liite-tunniste}
               :lahetysaika "2026-01-01T12:00:00Z"}
     :liite-poisto {:tunniste {:id liite-tunniste}}}))

(defn- hae-vapaa-tarkastus-ulkoinen-id []
  (let [id (rand-int 10000)]
    (if (empty? (q (str "SELECT id FROM tarkastus WHERE ulkoinen_id = " id ";")))
      id
      (recur))))

(deftest liite-tarkastukselle-tallentuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id (hae-vapaa-tarkastus-ulkoinen-id)
        liite-tunniste (+ 100000000 (rand-int 100000000))
        nimi "liite-api-tarkastus.txt"
        kayttaja-id (ffirst (q (format "SELECT id FROM kayttaja WHERE kayttajanimi = '%s'" kayttaja)))]
    (anna-kirjoitusoikeus kayttaja)
    (u (format "INSERT INTO tarkastus (urakka, aika, tyyppi, ulkoinen_id, poistettu, luoja, lahde) VALUES (%s, CURRENT_TIMESTAMP, 'soratie', %s, FALSE, %s, 'harja-api')"
               urakka-id kohde-id kayttaja-id))
    (try
      (let [pyynto (liite-pyynto "tarkastus" kohde-id liite-tunniste nimi)
            ennen (ffirst (q (format "SELECT count(*) FROM liite WHERE urakka = %s" urakka-id)))
            vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti pyynto)
            toinen-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti pyynto)]
        (is (= 200 (:status vastaus)))
        (is (= "Liite vastaanotettu onnistuneesti"
               (get (cheshire/decode (:body vastaus) true) :ilmoitukset)))
        (is (= 200 (:status toinen-vastaus)))
        (is (= (inc ennen)
               (ffirst (q (format "SELECT count(*) FROM liite WHERE urakka = %s AND ulkoinen_id = '%s'"
                                  urakka-id liite-tunniste)))))
        (is (= 1
               (ffirst (q (format "SELECT count(*) FROM tarkastus_liite hl JOIN liite l ON l.id = hl.liite WHERE hl.tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s LIMIT 1) AND l.ulkoinen_id = '%s'"
                                  urakka-id kohde-id liite-tunniste))))))
      (finally
        (u (format "DELETE FROM tarkastus_liite WHERE tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s)"
                   urakka-id kohde-id))
        (u (format "DELETE FROM liite WHERE urakka = %s AND ulkoinen_id = '%s'"
                   urakka-id liite-tunniste))
        (u (format "DELETE FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s"
                   urakka-id kohde-id))))))
