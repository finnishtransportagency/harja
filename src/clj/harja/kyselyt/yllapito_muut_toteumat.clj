(ns harja.kyselyt.yllapito-muut-toteumat
  (:require [jeesql.core :refer [defqueries]]))

(declare luo-uusi-urakan_laskentakohde<! hae-urakan-laskentakohteet tiemerkinnan-yksikkohintaisen-toteuman-urakka
  muun-toteuman-urakka hae-muut-tyot hae-muu-tyo paivita-muu-tyo<! luo-uusi-muu-tyo<!
  luo-tiemerkintaurakan-yksikkohintainen-tyo<! hae-tiemerkintaurakan-yksikkohintaiset-tyot
  paivita-tiemerkintaurakan-yksikkohintainen-tyo<!)

(defqueries "harja/kyselyt/yllapito_toteumat.sql"
  {:positional? true})
