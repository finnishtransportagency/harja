(ns harja.palvelin.integraatiot.turi.sanomat.turvallisuuspoikkeama-test
  (:require [clojure.test :refer :all]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.turi.sanomat.turvallisuuspoikkeama :as sanoma]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
            [harja.tyokalut.xml :as xml]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
            [harja.kyselyt.kommentit :as kommentit]
            [clojure.data.zip.xml :as z])
  (:import (org.apache.commons.io IOUtils)))

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
                                 (harja.palvelin.komponentit.liitteet/->Liitteet nil)
                                 [:db :virustarkistus])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn pura-liite [liite]
  (String. (dekoodaa-base64 (.getBytes liite))))

(defn testaa-turpon-sanoman-muodostus [id]
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        suora-liitetiedosto "suora liite"
        kommentin-liitetiedosto "kommentin liite"
        suoran-liitetiedoston-sisalto (IOUtils/toByteArray suora-liitetiedosto)
        kommentin-liitetiedoston-sisalto (IOUtils/toByteArray kommentin-liitetiedosto)
        suora-liite-id (:id (liitteet/luo-liite
                              liitteiden-hallinta
                              nil
                              (hae-oulun-alueurakan-2014-2019-id)
                              "testi.txt"
                              "text/plain"
                              581
                              suoran-liitetiedoston-sisalto
                              nil
                              "harja-ui"))
        kommentin-liite-id (:id (liitteet/luo-liite
                                  liitteiden-hallinta
                                  nil
                                  (hae-oulun-alueurakan-2014-2019-id)
                                  "testi.txt"
                                  "text/plain"
                                  581
                                  kommentin-liitetiedoston-sisalto
                                  nil
                                  "harja-ui"))
        kommentti-id (:id (kommentit/luo-kommentti<! (:db jarjestelma) nil "kommentti" kommentin-liite-id nil nil))
        _ (u (format "INSERT INTO turvallisuuspoikkeama_liite(turvallisuuspoikkeama,liite)
                      VALUES (%s, %s);" id suora-liite-id))
        _ (u (format "INSERT INTO turvallisuuspoikkeama_kommentti (turvallisuuspoikkeama, kommentti)
                      VALUES (%s, %s);" id, kommentti-id))
        data (turi/hae-turvallisuuspoikkeama
               (:liitteiden-hallinta jarjestelma)
               (:db jarjestelma)
               id)]
    (is (= (count (:liitteet data)) 2))
    (let [xml (sanoma/muodosta data)
          xml-data (xml/lue xml)
          liitteet (z/xml-> xml-data :poikkeamaliite :tiedosto z/text)]
      (is (xml/validi-xml? "xsd/turi/" "poikkeama-rest.xsd" xml) "Tehty sanoma on XSD-skeeman mukainen")

      (is (= (pura-liite (first liitteet)) suora-liitetiedosto) "Suora liite on myös XML-sanomassa")
      (is (= (pura-liite (second liitteet)) kommentin-liitetiedosto) "Kommentin liite on myös XML-sanomassa"))

    (u (str "DELETE FROM turvallisuuspoikkeama_liite WHERE liite = " suora-liite-id ";"))
    (u (str "DELETE FROM liite WHERE id = " suora-liite-id ";"))
    (is (= (ffirst (q "SELECT COUNT(*) FROM turvallisuuspoikkeama_liite")) 0))))

(deftest sanoman-muodostus-toimii-yhdelle-turpolle
  ;; Sanomien muodostuksen testaus kaikille testidatan turpoille
  ;; kattaa myös tämän yksittäisen turpon, tämä on tarkoitettu lähinnä debuggaukseen
  (let [id (first (flatten (q "SELECT id FROM turvallisuuspoikkeama
                               WHERE tapahtuman_otsikko = 'Torni kaatui Ernon päälle';")))]
    (testaa-turpon-sanoman-muodostus id)))

(deftest sanomien-muodostus-toimii-kaikille-turpoille
  (let [turpo-idt (sort (flatten (q "SELECT id FROM turvallisuuspoikkeama")))]
    (log/debug "Validoidaan turpo-idt: " (pr-str turpo-idt))
    (doseq [id turpo-idt]
      (testaa-turpon-sanoman-muodostus id))))

(deftest tyotuntien-lahetys)
