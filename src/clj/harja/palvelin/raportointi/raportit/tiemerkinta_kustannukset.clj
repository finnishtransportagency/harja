(ns harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset
  "Tiemerkinnän kustannuks raportit"
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.kayttaja :as kayttaja]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko rivi]]))


(defn sakot-ja-bonukset [db user {:keys [urakkatyyppi parametrit] :as params}]
  (let [{:keys [alkupvm loppupvm]} parametrit
        
        ;; {:alkupvm #inst "2024-12-31T22:00:00.000-00:00", :loppupvm #inst "2025-12-31T21:59:59.000-00:00", :urakkatyyppi :tiemerkinta, :kasittelija :pdf, :urakka-id 12} 
        _ (println "\n \n Params: " params)
        ]
    

  

    [:raportti {:orientaatio :landscape
                :nimi "TODO sakot, bonukset"
                :lyhennetty-tiedostonimi true}
     ;;
     ]))

(defn muut-kustannukset [db user {:keys [urakkatyyppi parametrit]}]
  (let [{:keys [alkupvm loppupvm]} parametrit]

    [:raportti {:orientaatio :landscape
                :nimi "TODO muut kustannukset"
                :lyhennetty-tiedostonimi true}
     ;;
     ]))
