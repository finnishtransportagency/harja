(ns harja.palvelin.raportointi.raportit.kanavien-maksuerat
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/palvelin/raportointi/raportit/kanavien_laskutusyhteenveto.sql")

(defn urakan-maksuerien-summat [db urakka]

  )



(def kama [{:akillinen-hoitotyo 0.0M,
            :yksikkohintainen 2000.0M,
            :sakko -31526.66600M,
            :muu 11000.0M,
            :indeksi 8345.2044093231159362450000000M,
            :bonus 21000.0M,
            :lisatyo 2000.0M,
            :urakka_id 4,
            :kokonaishintainen 42010.0M,
            :tpi_id 4}
           {:akillinen-hoitotyo 3000.0M,
            :yksikkohintainen 11882.50M,
            :sakko -1434.0M,
            :muu 1000.0M,
            :indeksi 2410.41666666666671345000M,
            :bonus 0.0M,
            :lisatyo 10000.0M,
            :urakka_id 4,
            :kokonaishintainen 0.0M,
            :tpi_id 5}
           {:akillinen-hoitotyo 0.0M,
            :yksikkohintainen 0.0M,
            :sakko -22860.0M,
            :muu 0.0M,
            :indeksi -1616.36015325670619390000M,
            :bonus 0.0M,
            :lisatyo 0.0M,
            :urakka_id 4,
            :kokonaishintainen 120000.0M,
            :tpi_id 6}])