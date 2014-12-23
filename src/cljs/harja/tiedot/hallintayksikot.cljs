(ns harja.tiedot.hallintayksikot
  "Hallinnoi hallintayksiköiden tietoja"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]))

(def hallintayksikot (atom nil))
 
;; Kun käyttäjätiedot saapuvat, hae relevantit hallintayksiköt
(t/kuuntele! :kayttajatiedot
             (fn [kayttaja]
               (k/request! :hallintayksikot
                           :tie ;; FIXME: tämä otettava käyttäjän tiedoista 
                           #(reset! hallintayksikot
                                    (mapv (fn [hy]
                                            (assoc hy :type :hy)) %)))))

                 
 
