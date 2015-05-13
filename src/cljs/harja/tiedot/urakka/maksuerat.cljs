(ns harja.tiedot.urakka.maksuerat
  "Tämä nimiavaruus hallinnoi urakan maksueria."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-maksuerat [urakka-id]
    ;(k/post! :hae-urakan-kayttajat urakka-id)) TODO
    (atom [{:nimi "asd" :numero "4" :tyyppi "asd" :maksueran-summa "4" :kustannussuunnitelma-summa "5" :lahetetty "11052015"}
           {:nimi "asd" :numero "4" :tyyppi "asd" :maksueran-summa "4" :kustannussuunnitelma-summa "5" :lahetetty "11052015"}
           {:nimi "asd" :numero "4" :tyyppi "asd" :maksueran-summa "4" :kustannussuunnitelma-summa "5" :lahetetty "11052015"}
           {:nimi "asd" :numero "4" :tyyppi "asd" :maksueran-summa "4" :kustannussuunnitelma-summa "5" :lahetetty "11052015"}]))
    )

