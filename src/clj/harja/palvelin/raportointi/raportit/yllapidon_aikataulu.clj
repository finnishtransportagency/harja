(ns harja.palvelin.raportointi.raportit.yllapidon-aikataulu
  (:require [harja.ui.aikajana :as aj]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
            [harja.domain.aikataulu :as aikataulu]
            [harja.kyselyt.urakat :as urakat-q]))

(defn- parametrit-sopimus-idlla [db {urakka-id :urakka-id :as parametrit}]
  (assoc parametrit
         :sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)))

(defn suorita [db user parametrit]
  (let [parametrit (parametrit-sopimus-idlla db parametrit)
        aikataulu (yllapitokohteet/hae-urakan-aikataulu db user parametrit)]
    #_(println "AIKATAULU: "(pr-str aikataulu))
    [:raportti {:nimi "Ylläpidon aikataulu"}
     [:aikajana {}
      (map aikataulu/aikataulurivi-jana aikataulu)]]))
