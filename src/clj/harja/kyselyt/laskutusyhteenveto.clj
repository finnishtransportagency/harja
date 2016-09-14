(ns harja.kyselyt.laskutusyhteenveto
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.core :as t]))

(defqueries "harja/kyselyt/laskutusyhteenveto.sql"
  {:positional? true})

(defn hae-urakat-joille-laskutusyhteenveto-ajetaan
  [db]
  (let [nyt (pvm/nyt)
        vuosi (pvm/vuosi nyt)
        kk (- (pvm/kuukausi nyt) 2)
        viimeinen-paiva (t/day (t/last-day-of-the-month vuosi (inc kk)))
        alku (pvm/luo-pvm vuosi kk 1)
        loppu (pvm/luo-pvm vuosi kk viimeinen-paiva)]
    (hae-urakat-joille-laskutusyhteenveto-voidaan-tehda
     db {:alku alku :loppu loppu})))
