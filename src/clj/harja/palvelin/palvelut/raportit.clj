(ns harja.palvelin.palvelut.raportit
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]))


(defrecord Raportit []
  component/Lifecycle
  (start [{raportointi :raportointi
           http :http-palvelin
           :as this}]
    (julkaise-palvelut http
                       :hae-raportit
                       (fn [user]
                         (reduce-kv (fn [raportit nimi raportti]
                                      (assoc raportit
                                             nimi (dissoc raportti :suorita)))
                                    {}
                                    (hae-raportit raportointi)))
                       
                       :suorita-raportti
                       (fn [user raportti]
                         (suorita-raportti raportointi user raportti)))
     
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :suorita-raportti)
    this))
