(ns harja.palvelin.palvelut.raportit
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.palvelin.komponentit.pdf-vienti :refer [rekisteroi-pdf-kasittelija! poista-pdf-kasittelija!] :as pdf-vienti]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]
            [harja.tyokalut.xsl-fo :as fo])
  (:import (java.sql Timestamp)))


(defrecord Raportit []
  component/Lifecycle
  (start [{raportointi :raportointi
           http        :http-palvelin
           db          :db
           pdf-vienti  :pdf-vienti
           :as         this}]

    (julkaise-palvelut http
                       :hae-raportit
                       (fn [user]
                         (reduce-kv (fn [acc nimi raportti]
                                      ;; Otetaan suoritus fn pois frontille lähetettävästä
                                      (assoc acc nimi (dissoc raportti :suorita)))
                                    {}
                                    (hae-raportit raportointi)))

                       :suorita-raportti
                       (fn [user raportti]
                         (suorita-raportti raportointi user raportti)))

    this)

  (stop [{http :http-palvelin pdf-vienti :pdf-vienti :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :suorita-raportti)
    this))
