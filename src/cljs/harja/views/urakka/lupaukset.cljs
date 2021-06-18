(ns harja.views.urakka.lupaukset
  "Lupausten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [cljs.core.async :refer [<!]]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn lupaukset-alempi-valilehti [{:keys [nakyma urakka]}]
  [:h1 "Lupaukset alempi välilehti"])

(defn- valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :lupaukset (#{:teiden-hoito} tyyppi)

    false))

(defn lupaukset-paatason-valilehti [ur]
  (komp/luo
    (fn [{:keys [tyyppi] :as ur}]
      ;; vain MHU-urakoissa halutaan Lupaukset, jolloin alatabit näkyviin. Muutoin suoraan Välitavoitteet sisältö
      (if (= tyyppi :teiden-hoito)
        [bs/tabs
        {:style :tabs :classes "tabs-taso2"
         ;; huom: avain yhä valitavoitteet, koska Rooli-excel ja oikeudet
         :active (nav/valittu-valilehti-atom :valitavoitteet)}

        "Lupaukset" :lupaukset
        (when (valilehti-mahdollinen? :lupaukset ur)
          [lupaukset-alempi-valilehti {:nakyma tyyppi
                                       :urakka ur}])

        "Välitavoitteet" :valitavoitteet-nakyma
        [valitavoitteet/valitavoitteet ur]]
        [valitavoitteet/valitavoitteet ur]))))