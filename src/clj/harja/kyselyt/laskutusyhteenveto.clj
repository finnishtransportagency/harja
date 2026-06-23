(ns harja.kyselyt.laskutusyhteenveto
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/laskutusyhteenveto.sql"
            {:positional? true})

(declare hae-laskutusyhteenvedon-tiedot hae-laskutusyhteenvedon-tiedot-tuotekohtainen
  hae-urakat-joille-laskutusyhteenveto-voidaan-tehda
  poista-urakan-kaikki-muistetut-laskutusyhteenvedot!
  laske-erilliskustannuksen-indeksi)
