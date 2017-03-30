(ns harja.views.urakka.paallystyksen-maksuerat
  "Päällystysurakan maksuerät"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.paikkaus :as paikkaus]
            [harja.ui.kommentit :as kommentit]
            [harja.domain.paikkausilmoitus :as minipot]
            [harja.views.kartta :as kartta]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.tierekisteri :as tierekisteri-domain]
            [harja.ui.napit :as napit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn maksuerat [urakka]
  (komp/luo
    (komp/lippu paikkaus/paikkausilmoitukset-nakymassa?)

    (fn [urakka]
      [:div "Work in progress"])))
