(ns harja.palvelin.ajastetut-tehtavat.laskutusyhteenvedot
  "Ajastettu tehtävä laskutusyhteenvetojen muodostamiseksi valmiiksi välimuistiin"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.laskutusyhteenveto :as q]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.periodic :as time-periodic]
            [chime :as chime]))

(defn- muodosta-laskutusyhteenveto [db alku loppu urakka urakkatyyppi]
  (let [[hk-alku hk-loppu :as hk] (pvm/paivamaaran-hoitokausi alku)
        sql {:urakka            urakka
             :hk_alkupvm        hk-alku
             :hk_loppupvm       hk-loppu
             :aikavali_alkupvm  alku
             :aikavali_loppupvm loppu}]
    (when-not (= hk (pvm/paivamaaran-hoitokausi loppu))
      (log/error "Alku- ja loppupäivämäärä laskutusyhteenvedolle eivät ole samalla hoitokaudella"))
    (if (= urakkatyyppi "teiden-hoito")
      (q/hae-laskutusyhteenvedon-tiedot-teiden-hoito db sql)
      (q/hae-laskutusyhteenvedon-tiedot db sql))))

(defn- muodosta-laskutusyhteenvedot [db]
  (let [nyt (pvm/nyt)
        vuosi (pvm/vuosi nyt)
        kk (- (pvm/kuukausi nyt) 2)
        viimeinen-paiva (t/day (t/last-day-of-the-month vuosi (inc kk)))
        alku (pvm/luo-pvm vuosi kk 1)
        loppu (pvm/luo-pvm vuosi kk viimeinen-paiva)]
    (log/info "Muodostetaan laskutusyhteenvedot valmiiksi")
    (doseq [{:keys [id nimi tyyppi]} (q/hae-urakat-joille-laskutusyhteenveto-voidaan-tehda
                               db {:alku alku :loppu loppu})]
      (log/info "Muodostetaan laskutusyhteenveto valmiiksi urakalle: " nimi ". Urakkatyyppi: " tyyppi ".")
      (lukot/aja-lukon-kanssa
       db (str "laskutusyhteenveto:" id)
       #(try
          (muodosta-laskutusyhteenveto db alku loppu id tyyppi)
          (catch Throwable t
            (log/error t "Virhe muodostettaessa laskutusyhteenvetoa, urakka: " id ", aikavali "
                       alku " -- " loppu)))))))

(defn- ajasta [db]
  (let [aika (t/plus (t/now) (t/days 1))
        ensimmainen (pvm/suomen-aikavyohykkeessa
                     (t/date-time (t/year aika) (t/month aika) (t/day aika)
                                  4 30))]
    (log/info "Ajastetaan laskutusyhteenvetojen muodostus päivittäin, ensimmäinen: " ensimmainen)
    (chime/chime-at (time-periodic/periodic-seq
                     ensimmainen
                     (t/days 1))
                    (fn [_]
                      (muodosta-laskutusyhteenvedot db)))))

(defrecord LaskutusyhteenvetojenMuodostus []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this ::laskutusyhteenvetojen-ajastus
           (ajasta db)))
  (stop [{poista ::laskutusyhteenvetojen-ajastus :as this}]
    (poista)
    (dissoc this ::laskutusyhteenvetojen-ajastus)))
