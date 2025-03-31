(ns harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset
  "Tiemerkinnän kustannuks raportit"
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.kayttaja :as kayttaja]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko rivi]]))


(defn sakot-ja-bonukset [db user {:keys [urakkatyyppi parametrit]}]
  (let [{:keys [alkupvm loppupvm]} parametrit]

    [:raportti {:orientaatio :landscape
                :nimi "TODO"
                :lyhennetty-tiedostonimi true}
     ;;
     ]))

(defn muut-kustannukset [db user {:keys [urakkatyyppi parametrit]}]
  (let [{:keys [alkupvm loppupvm]} parametrit]

    [:raportti {:orientaatio :landscape
                :nimi "TODO"
                :lyhennetty-tiedostonimi true}
     ;;
     ]))
