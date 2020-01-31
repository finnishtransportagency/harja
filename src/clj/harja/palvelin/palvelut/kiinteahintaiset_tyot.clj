(ns harja.palvelin.palvelut.kiinteahintaiset-tyot
  (:require [harja.kyselyt.kiinteahintaiset-tyot :as q]
            [harja.domain.oikeudet :as oikeudet]))


(defn hae-urakan-kiinteahintaiset-tyot
  "Funktio palauttaa urakan kiinteahintaiset työt. Käytetään teiden hoidon urakoissa (MHU)."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (q/hae-kiinteahintaiset-tyot db {:urakka urakka-id}))
