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
            [harja.tyokalut.xml :as xml]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io File)
           (org.apache.commons.io IOUtils)))

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
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        tiedosto "test/resurssit/sampo/maksuera_ack.xml"
        tiedoston-sisalto (IOUtils/toByteArray (io/input-stream tiedosto))
        luotu-liite (liitteet/luo-liite
                      liitteiden-hallinta
                      nil
                      (hae-oulun-alueurakan-2014-2019-id)
                      "maksuera_ack.xml"
                      "text/xml"
                      581
                      tiedoston-sisalto
                      nil
                      "harja-ui")
        liite-id (:id luotu-liite)
        _ (u (str "INSERT INTO
                   turvallisuuspoikkeama_liite(turvallisuuspoikkeama,liite)
                  VALUES (" id "," liite-id ");"))
        data (turi/hae-turvallisuuspoikkeama
               (:liitteiden-hallinta jarjestelma)
               (:db jarjestelma)
               id)
        liite-datassa (slurp (:data (first (:liitteet data))))]
    ;; Data, josta sanoma muodostetaan, sisältää liitteet oikein
    (is (= (count (:liitteet data)) 1))
    (is (str/starts-with? liite-datassa "<?xml version=") "Liite löytyy datasta")
    (let [xml (sanoma/muodosta data)
          xml-mappina (xml/lue xml)
          xml-liitteet (-> xml-mappina first :content last :content second :content)]
      (is (xml/validi-xml? "xsd/turi/" "poikkeama-rest.xsd" xml) "Tehty sanoma on XSD-skeeman mukainen")
      (is (= (slurp xml-liitteet) liite-datassa) "Liite on myös XML-sanomassa"))
    (u (str "DELETE FROM turvallisuuspoikkeama_liite WHERE liite = " liite-id ";"))
    (u (str "DELETE FROM liite WHERE id = " liite-id ";"))
    (is (= (ffirst (q "SELECT COUNT(*) FROM turvallisuuspoikkeama_liite")) 0))))

(deftest sanoman-muodostus-toimii-yhdelle-turpolle
  ;; Yksittäisen sanoman testaus helpottamaan debuggausta.
  ;; Sanomien muodostuksen testaus kaikille testidatan turpoille
  ;; kattaa tämän.
  (let [id (first (flatten (q "SELECT id FROM turvallisuuspoikkeama")))]
    (testaa-turpon-sanoman-muodostus id)))

(deftest sanomien-muodostus-toimii-kaikille-turpoille
  (let [turpo-idt (sort (flatten (q "SELECT id FROM turvallisuuspoikkeama")))]
    (log/debug "Validoidaan turpo-idt: " (pr-str turpo-idt))
    (doseq [id turpo-idt]
      (testaa-turpon-sanoman-muodostus id))))