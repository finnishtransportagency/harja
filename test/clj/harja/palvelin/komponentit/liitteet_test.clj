(ns harja.palvelin.komponentit.liitteet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.komponentit.tietokanta :as tietokanta])
  (:import (org.apache.commons.io IOUtils)))

(defn poista-liite [liite-id]
  (u (str "DELETE FROM liite WHERE id = " liite-id ";")))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :liitteiden-hallinta
                        (component/using
                          (harja.palvelin.komponentit.liitteet/->Liitteet nil)
                          [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-xml-liite
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        tiedosto "test/resurssit/sampo/maksuera_ack.xml"
        tiedoston-sisalto-tekstina (slurp tiedosto)
        tiedoston-sisalto (IOUtils/toByteArray (io/input-stream tiedosto))
        luotu-liite (liitteet/luo-liite liitteiden-hallinta nil 1 "maksuera_ack.xml" "text/xml" 581 tiedoston-sisalto nil "harja-ui")
        liite-id (:id luotu-liite)
        luettu-liite (liitteet/lataa-liite liitteiden-hallinta liite-id)
        liitteen-sisalto-tekstina (slurp (:data luettu-liite))]

    ;; (println luotu-liite)

    (is (= tiedoston-sisalto-tekstina liitteen-sisalto-tekstina) "Luetun liitteen sisältö on sama kuin mitä lähdetiedoston.")
    (is (not (:pikkukuva luettu-liite)) "XML-tiedostolla ei saa olla pikkukuvaa.")

    (poista-liite liite-id)))

(deftest tallenna-kuvaliite
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        tiedosto "dev-resources/images/harja-brand-text.png"
        tiedoston-sisalto (IOUtils/toByteArray (io/input-stream tiedosto))
        luotu-liite (liitteet/luo-liite liitteiden-hallinta nil 1 "harja-brand-text.png" "image/png" 3 tiedoston-sisalto nil "harja-ui")
        liite-id (:id luotu-liite)
        luettu-pikkukuva (liitteet/lataa-pikkukuva liitteiden-hallinta liite-id)]

    (is luettu-pikkukuva "Kuvatiedostolla pitää olla pikkukuva.")

    (poista-liite liite-id)))
