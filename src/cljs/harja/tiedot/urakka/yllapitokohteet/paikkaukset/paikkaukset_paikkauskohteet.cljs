(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.grid :as grid]
            [harja.domain.paikkaus :as paikkaus]
            [taoensso.timbre :as log]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauskohteet [{:testinro "14566"
                                   :testinimi "Kaislajärven suora"
                                   :testitila "Ehdotettu"
                                   :testimenetelma "UREM"
                                   :testisijainti "23423/121231"
                                   :testiaikataulu "1.6. - 31.8.2021 (arv.)"}
                                  {:testinro "14567"
                                   :testinimi "Mount Utajärvi VT1"
                                   :testitila "Tilattu"
                                   :testimenetelma "KTVA"
                                   :testisijainti "23423/121231"
                                   :testiaikataulu "1.6. - 31.8.2021 (arv.)"}
                                  {:testinro "14568"
                                   :testinimi "Kiikelin monttu"
                                   :testitila "Valmis"
                                   :testimenetelma "SIP"
                                   :testisijainti "23423/121231"
                                   :testiaikataulu "1.6. - 14.6.2021"}]}))

