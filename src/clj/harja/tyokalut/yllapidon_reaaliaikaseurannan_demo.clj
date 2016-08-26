(ns harja.tyokalut.yllapidon-reaaliaikaseurannan-demo
  (:require [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.suljetut-tieosuudet :as q-suljetut-tieosuudet]))

(defn aja []
  (let [tietokanta {:palvelin "localhost"
                    :tietokanta "harja"
                    :portti 7771
                    :yhteyspoolin-koko 16
                    :kayttaja "flyway"
                    :salasana "migrate123"}
        db (tietokanta/luo-tietokanta tietokanta true)
        suljettutieosuus {:jarjestelma "Harja"
                          :osuusid 123456789
                          :alkux 432040
                          :alkuy 7213557
                          :loppux 438382
                          :loppuy 7220022
                          :asetettu ()
                          :kaistat (konv/seq->array (:kaistat suljettu-tieosuus))
                          :ajoradat (konv/seq->array (:ajoradat suljettu-tieosuus))
                          :yllapitokohde kohde-id
                          :kirjaaja (:id kayttaja)
                          :tr_tie (:tie tr-osoite)
                          :tr_aosa (:aosa tr-osoite)
                          :tr_aet (:aet tr-osoite)
                          :tr_losa (:losa tr-osoite)
                          :tr_let (:let tr-osoite)}
        suljettutieosuusid (:id (first (q-suljetut-tieosuudet/luo-suljettu-tieosuus<! db suljettutieosuus)))]




    )

  )
