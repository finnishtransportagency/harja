(ns harja.palvelin.palvelut.raportit
  (:require  [com.stuartsierra.component :as component]
             [clojure.string :as str]
             [taoensso.timbre :as log]

             [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
             [harja.kyselyt.konversio :as konv]
             [harja.domain.roolit :as roolit]
             [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
             [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
             [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]))


(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm] :as tiedot}]
  (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp
          (map #(konv/decimal->double % :kht_laskutettu_hoitokaudella_ennen_aikavalia))
          (map #(konv/decimal->double % :kht_laskutetaan_aikavalilla))
          (map #(konv/decimal->double % :yht_laskutettu_hoitokaudella_ennen_aikavalia))
          (map #(konv/decimal->double % :yht_laskutetaan_aikavalilla)))
              (laskutus-q/hae-laskutusyhteenvedon-tiedot db (konv/sql-date hk_alkupvm)
                                              (konv/sql-date hk_loppupvm)
                                              (konv/sql-date aikavali_alkupvm)
                                              (konv/sql-date aikavali_loppupvm)
                                              urakka-id)))

(defrecord Raportit []
  component/Lifecycle
  (start [{raportointi :raportointi
           http :http-palvelin
           db :db
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
                         (suorita-raportti raportointi user raportti))

                       :hae-laskutusyhteenvedon-tiedot
                       (fn [user tiedot]
                         (hae-laskutusyhteenvedon-tiedot db user tiedot)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :suorita-raportti
                     :hae-laskutusyhteenvedon-tiedot)
    this))
