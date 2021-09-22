(ns harja.palvelin.palvelut.raportit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.komponentit.pdf-vienti :refer [rekisteroi-pdf-kasittelija! poista-pdf-kasittelija!] :as pdf-vienti]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.raportit :as q]))

(defn hae-raporttien-suoritustiedot 
  [db user parametrit]
  ;; vaadi lukuoikeus
  (q/hae-raporttien-suoritustiedot db))

(defrecord Raportit []
  component/Lifecycle
  (start [{raportointi :raportointi
           http        :http-palvelin
           db          :db
           pdf-vienti  :pdf-vienti
           :as         this}]

    (julkaise-palvelu http
                      :hae-raportit
                      (fn [user]
                        (oikeudet/ei-oikeustarkistusta!)
                        (reduce-kv (fn [acc nimi raportti]
                                     ;; Otetaan suoritus fn ja koodi pois frontille lähetettävästä
                                     (assoc acc nimi (dissoc raportti :suorita :koodi)))
                                   {}
                                   (hae-raportit raportointi))))

    (julkaise-palvelu http :suorita-raportti
                      (fn [user raportti]
                        (suorita-raportti raportointi user raportti))
                      {:trace false})
    
    (julkaise-palvelu http :hae-raporttien-suoritustiedot 
                      (fn [user parametrit]
                        (hae-raporttien-suoritustiedot db user parametrit)))

    this)

  (stop [{http :http-palvelin pdf-vienti :pdf-vienti :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :hae-raporttien-suoritustiedot
                     :suorita-raportti)
    this))
