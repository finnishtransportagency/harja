(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma-test
  (:require [clojure.test :refer :all]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma :as sanoma]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
            [clj-time.core :as t]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus])
  (:import (java.io File)))

(defn jarjestelma-fixture [testit]
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :virustarkistus (virustarkistus/luo-virustarkistus
                            {:url "http://localhost:8080/scan"})
          :liitteiden-hallinta (component/using
                                 (harja.palvelin.komponentit.liitteet/->Liitteet)
                                 [:db :virustarkistus])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn testaa-turpon-sanoman-muodostus [id]
  (let [testiliite-polku (str "/tmp/harja_" (hash (t/now)) ".txt")
        testiliite-tiedosto (do
                              (.createNewFile (File. testiliite-polku))
                              (spit testiliite-polku "testi")
                              (File. testiliite-polku))
        liite (liitteet/luo-liite
                (:liitteiden-hallinta jarjestelma)
                1
                (hae-oulun-alueurakan-2014-2019-id)
                "seppo.txt"
                "text/plain"
                100
                testiliite-tiedosto
                "Muhkea liite!"
                "harja-api")
        _ (u (str "INSERT INTO
                   turvallisuuspoikkeama_liite(turvallisuuspoikkeama,liite)
                  VALUES (" id "," (:id liite) ");"))
        data (turi/hae-turvallisuuspoikkeama
               (:liitteiden-hallinta jarjestelma)
               (:db jarjestelma)
               id)
        xml (sanoma/muodosta data)]
    (log/debug "Aloitetaan sanoman validointi: " (pr-str xml))
    (is (xml/validi-xml? "xsd/turi/" "poikkeama-rest.xsd" xml) "Tehty sanoma on XSD-skeeman mukainen")
    (.delete testiliite-tiedosto)))

(deftest sanoman-muodostus-toimii-yhdelle-turpolle
  (let [id (first (flatten (q "SELECT id FROM turvallisuuspoikkeama")))]
    (testaa-turpon-sanoman-muodostus id)))

(deftest sanomien-muodostus-toimii-kaikille-turpoille
  (let [turpo-idt (sort (flatten (q "SELECT id FROM turvallisuuspoikkeama")))]
    (log/debug "Validoidaan turpo-idt: " (pr-str turpo-idt))
    (doseq [id turpo-idt]
      (testaa-turpon-sanoman-muodostus id))))