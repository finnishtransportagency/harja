(ns harja.palvelin.ajastetut-tehtavat.laskutusyhteenvedot
  "Ajastettu tehtävä laskutusyhteenvetojen muodostamiseksi valmiiksi välimuistiin"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.laskutusyhteenveto :as q]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.konversio :as konv]))

(defn- muodosta-laskutusyhteenveto [db alku loppu urakka]
  (let [[hk-alku hk-loppu :as hk] (pvm/paivamaaran-hoitokausi alku)
        sql {:urakka            urakka
             :hk_alkupvm        hk-alku
             :hk_loppupvm       hk-loppu
             :aikavali_alkupvm  alku
             :aikavali_loppupvm loppu}]
    (when-not (= hk (pvm/paivamaaran-hoitokausi loppu))
      (log/error "Alku- ja loppupäivämäärä laskutusyhteenvedolle eivät ole samalla hoitokaudella"))
    (q/hae-laskutusyhteenvedon-tiedot db sql)))

;; 30min lukko, estää molemmilta nodeilta ajamisen
(def lukon-vanhenemisaika-sekunteina (* 60 30))

(defn- muodosta-laskutusyhteenvedot [db]
  (let [[alku loppu] (map #(-> %
                               pvm/dateksi
                               konv/sql-date)
                          (pvm/ed-kk-date-vektorina (pvm/joda-timeksi (pvm/nyt))))]
    (log/info "Muodostetaan laskutusyhteenvedot valmiiksi")
    (doseq [{:keys [id nimi]} (q/hae-urakat-joille-laskutusyhteenveto-voidaan-tehda
                                       db {:alku alku
                                           :loppu loppu})]
      (log/info "Muodostetaan laskutusyhteenveto valmiiksi urakalle: " nimi)
      (lukot/yrita-ajaa-lukon-kanssa
        db (str "laskutusyhteenveto:" id)
        #(try
           (muodosta-laskutusyhteenveto db alku loppu id)
           (catch Throwable t
             (log/error t "Virhe muodostettaessa laskutusyhteenvetoa, urakka: " id ", aikavali "
                        alku " -- " loppu)))
        lukon-vanhenemisaika-sekunteina))))



(defn- ajasta [db]
  (log/info "Ajastetaan laskutusyhteenvetojen muodostus päivittäin")
  (ajastettu-tehtava/ajasta-paivittain [2 0 0]
                                       (fn [_]
                                         (muodosta-laskutusyhteenvedot db))))

(defrecord LaskutusyhteenvetojenMuodostus []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this ::laskutusyhteenvetojen-ajastus
           (ajasta db)))
  (stop [{poista ::laskutusyhteenvetojen-ajastus :as this}]
    (poista)
    (dissoc this ::laskutusyhteenvetojen-ajastus)))
