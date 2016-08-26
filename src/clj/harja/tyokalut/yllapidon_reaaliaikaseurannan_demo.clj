(ns harja.tyokalut.yllapidon-reaaliaikaseurannan-demo
  (:require [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.suljetut-tieosuudet :as q-suljetut-tieosuudet]
            [harja.kyselyt.tyokoneseuranta :as tks])
  (:import (java.util Calendar)
           (java.sql Timestamp)))

(defn arrayksi [db v]
  (with-open [conn (.getConnection (:datasource db))]
    (.createArrayOf conn "text" (to-array v))))

(defn paivita [db sql]
  (with-open [c (.getConnection (:datasource db))
              ps (.prepareStatement c (reduce str sql))]
    (.executeUpdate ps)))

(defn nyt []
  (new Timestamp (.getTime (.getTime (Calendar/getInstance)))))

(defn aja []
  (let [tietokanta {:palvelin "localhost"
                    :tietokanta "harja"
                    :portti 7771
                    :yhteyspoolin-koko 16
                    :kayttaja "flyway"
                    :salasana "migrate123"}
        urakka-id 348
        yllapitokohde-id 7
        alkux 570095
        alkuy 6771092
        loppux 574110
        loppuy 6774221
        db (tietokanta/luo-tietokanta tietokanta true)
        _ (paivita db "DELETE FROM suljettu_tieosuus WHERE osuus_id = 123456789;")
        suljettutieosuus {:jarjestelma "Harja"
                          :osuusid 123456789
                          :alkux alkux
                          :alkuy alkuy
                          :loppux loppux
                          :loppuy loppuy
                          :asetettu (nyt)
                          :kaistat (konv/seq->array [1])
                          :ajoradat (konv/seq->array [0])
                          :yllapitokohde yllapitokohde-id
                          :kirjaaja 13
                          :tr_tie 6
                          :tr_aosa 302
                          :tr_aet 4240
                          :tr_losa 304
                          :tr_let 688}
        suljettutieosuusid (:id (q-suljetut-tieosuudet/luo-suljettu-tieosuus<! db suljettutieosuus))]


    ;; Aseta TMA-aidat suljetun tieosuuden alkuun ja loppuun
    (tks/tallenna-tyokonehavainto
      db
      "Harja"
      "Solita Oy"
      "1060155-5"
      1000001
      (nyt)
      1000001
      "TMA-aita 1"
      alkux
      alkuy
      425
      urakka-id
      (arrayksi db ["turvalaite"]))

    (tks/tallenna-tyokonehavainto
      db
      "Harja"
      "Solita Oy"
      "1060155-5"
      1000002
      (nyt)
      1000002
      "TMA-aita 2"
      loppux
      loppuy
      425
      urakka-id
      (arrayksi db ["turvalaite"]))

    )

  )

