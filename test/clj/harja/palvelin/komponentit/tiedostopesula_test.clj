(ns harja.palvelin.komponentit.tiedostopesula-test
  "Testataan tp-clientin sisäinen logiikka ja vikatilanteiden hanskaus"
  (:require [harja.palvelin.komponentit.tiedostopesula :as sut]
            [clojure.java.io :as io]
            [clojure.test :as t]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.test :as t :refer [deftest is use-fixtures testing]])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root
   #'jarjestelma
   (fn [_]
     (component/start
      (component/system-map
       :db (tietokanta/luo-tietokanta testitietokanta)
       :tiedostopesula (component/using (sut/luo-tiedostopesula {:base-url "https://localhost:0/"}) [])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest testaa-pesula
  (let [tp (:tiedostopesula jarjestelma)
        tee-lahde #(io/input-stream (.getBytes "muka-pdf-sisalto"))]
    (is (some? tp))

    (let [muunnettu (sut/pdfa-muunna-file->file! tp (tee-lahde))]
      (is (nil? muunnettu)))

    (with-fake-http ["https://localhost:0/pdf/pdf2pdfa" "kekkis"]
      (let [muunnettu (sut/pdfa-muunna-file->file! tp (tee-lahde))]
        (is (= "kekkis" (slurp (io/input-stream muunnettu))))))))
