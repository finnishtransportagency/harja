(ns harja.tiedot.urakka.kohdeluettelo.paikkaus
  "Tämä nimiavaruus hallinnoi urakan paikkaustietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce paikkauskohteet-nakymassa? (atom false))
(defonce paikkausilmoitukset-nakymassa? (atom false))

(defn hae-paikkaustoteumat [urakka-id sopimus-id]
  (k/post! :urakan-paikkaustoteumat {:urakka-id  urakka-id
                                     :sopimus-id sopimus-id}))

(defn hae-paikkausilmoitus-paikkauskohteella [urakka-id sopimus-id paikkauskohde-id]
  (k/post! :urakan-paikkausilmoitus-paikkauskohteella {:urakka-id        urakka-id
                                                       :sopimus-id       sopimus-id
                                                       :paikkauskohde-id paikkauskohde-id}))

(defn tallenna-paikkausilmoitus [urakka-id sopimus-id lomakedata]
  (k/post! :tallenna-paikkausilmoitus {:urakka-id urakka-id
                                         :sopimus-id sopimus-id
                                         :paikkausilmoitus lomakedata}))