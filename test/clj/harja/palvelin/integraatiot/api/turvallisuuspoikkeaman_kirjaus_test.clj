(ns harja.palvelin.integraatiot.api.turvallisuuspoikkeaman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.turvallisuuspoikkeama :as turvallisuuspoikkeama]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [harja.kyselyt.konversio :as konv]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet) [:db])
    :turi (component/using
            (turi/->Turi {})
            [:db :integraatioloki :liitteiden-hallinta])
    :api-turvallisuuspoikkeama (component/using (turvallisuuspoikkeama/->Turvallisuuspoikkeama)
                                                [:http-palvelin :db :integraatioloki :liitteiden-hallinta :turi])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-turvallisuuspoikkeama
  (let [urakka (hae-oulun-alueurakan-2005-2012-id)
        tp-kannassa-ennen-pyyntoa (ffirst (q (str "SELECT COUNT(*) FROM turvallisuuspoikkeama;")))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/"urakka"/turvallisuuspoikkeama"]
                                         kayttaja portti
                                         (-> "test/resurssit/api/turvallisuuspoikkeama.json" slurp))]
    (cheshire/decode (:body vastaus) true)
    (is (= 200 (:status vastaus)))

    ;; Tarkista ensin perustasolla, että turpon kirjaus onnistui ja tiedot löytyvät
    (let [tp-kannassa-pyynnon-jalkeen (ffirst (q (str "SELECT COUNT(*) FROM turvallisuuspoikkeama;")))
          liite-id (ffirst (q (str "SELECT id FROM liite WHERE nimi = 'testitp36934853.jpg';")))
          tp-id (ffirst (q (str "SELECT id FROM turvallisuuspoikkeama WHERE kuvaus ='Aura-auto suistui tieltä väistäessä jalankulkijaa.'")))
          kommentti-id (ffirst (q (str "SELECT id FROM kommentti WHERE kommentti='Testikommentti';")))]

      (is (= (+ tp-kannassa-ennen-pyyntoa 1) tp-kannassa-pyynnon-jalkeen))
      (is (number? liite-id))
      (is (number? tp-id))
      (is (number? kommentti-id)))

    ;; Tiukka tarkastus, datan pitää olla kirjattu täysin oikein
    (let [uusin-tp (as-> (first (q (str "SELECT
                                  urakka,
                                  tapahtunut,
                                  kasitelty,
                                  sijainti,
                                  kuvaus,
                                  sairauspoissaolopaivat,
                                  sairaalavuorokaudet,
                                  tr_numero,
                                  tr_alkuosa,
                                  tr_alkuetaisyys,
                                  tr_loppuosa,
                                  tr_loppuetaisyys,
                                  vahinkoluokittelu
                                  FROM turvallisuuspoikkeama
                                  ORDER BY luotu DESC
                                  LIMIT 1")))
                         turpo
                         (assoc turpo 1 (c/from-sql-date (get turpo 1)))
                         (assoc turpo 2 (c/from-sql-date (get turpo 2)))
                         (assoc turpo 12 (into #{} (.getArray (get turpo 12))))
                         (assoc turpo 12 (into #{} (map keyword (get turpo 12)))))]

      (is (vector uusin-tp))
      (is (match uusin-tp [urakka
                           (_ :guard #(and (= (t/year %) 2016)
                                           (= (t/month %) 1)
                                           (= (t/day %) 30)))
                           (_ :guard #(and (= (t/year %) 2016)
                                           (= (t/month %) 2)
                                           (= (t/day %) 1)))
                           (_ :guard #(some? %))
                           "Aura-auto suistui tieltä väistäessä jalankulkijaa."
                           2
                           0
                           1234
                           1
                           100
                           73
                           20
                           #{:henkilovahinko}]
                 true)))))