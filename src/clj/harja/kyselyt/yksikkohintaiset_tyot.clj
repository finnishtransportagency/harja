(ns harja.kyselyt.yksikkohintaiset-tyot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yksikkohintaiset_tyot.sql"
  {:positional? false})

(declare
  listaa-urakan-yksikkohintaiset-tyot
  hae-urakan-sopimuksen-yksikkohintaiset-tehtavat
  paivita-urakan-yksikkohintainen-tyo!
  lisaa-urakan-yksikkohintainen-tyo<!
  merkitse-kustannussuunnitelmat-likaisiksi!
  hae-yksikkohintaiset-tyot-kuukausittain-urakalle
  hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle
  hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle-urakoittain
  hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle
  hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle-urakoittain
  hae-yksikkohintaiset-tyot-tehtavittain-summattuna-urakalle
  hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle
  hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle-urakoittain
  hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle
  hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle-urakoittain
  hae-yksikkohintaiset-tyot-per-paiva
  hae-vhu-urakan-hinnoittelemattomat-tehtavat)
