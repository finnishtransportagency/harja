(ns harja.views.urakka.lupaukset
  "Lupausten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [cljs.core.async :refer [<!]]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.lupaukset :as tiedot]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- lupausnappi
  "Pyöreä nappi, jonka numeroa voi tyypistä riippuen ehkä muokata."
  [teksti toiminto {:keys [tyyppi disabled?]}]
  (assert (#{:ennuste :toteuma :lupaus} tyyppi) "Tyypin on oltava ennuste, toteuma tai lupaus")
  [:div {:on-click toiminto

         :class ["lupausympyra" tyyppi]}
   [:h3 teksti]])

(defn- yhteenveto [e! app]
  [:div.yhteenveto
   [:div.kuukausi "Kesäkuu 2021"]
   [:div.pisteet
    [:div.lupausympyra 78]
    [lupausnappi 76 #(e! (tiedot/->MuokkaaLuvattujaPisteita))
     {:tyyppi "ennuste"}]
    [lupausnappi 76 #(e! (tiedot/->MuokkaaLuvattujaPisteita))
     {:tyyppi "lupaus"}]]])

(defn lupaukset-alempi-valilehti*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (tiedot/->HaeUrakanLupaustiedot (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])))))
    (komp/ulos #(e! (tiedot/->NakymastaPoistuttiin)))
    (fn [e! app]
      [:span.lupaukset
       [:h1 "Lupaukset alempi välilehti"]
       [yhteenveto e! app]])))

(defn- valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :lupaukset (#{:teiden-hoito} tyyppi)

    false))

(defn lupaukset-paatason-valilehti [ur]
  (fn [{:keys [tyyppi] :as ur}]
    ;; vain MHU-urakoissa halutaan Lupaukset, jolloin alatabit näkyviin. Muutoin suoraan Välitavoitteet sisältö
    (if (= tyyppi :teiden-hoito)
      [bs/tabs
       {:style :tabs :classes "tabs-taso2"
        ;; huom: avain yhä valitavoitteet, koska Rooli-excel ja oikeudet
        :active (nav/valittu-valilehti-atom :valitavoitteet)}

       "Lupaukset" :lupaukset
       (when (valilehti-mahdollinen? :lupaukset ur)
         [tuck/tuck tila/lupaukset lupaukset-alempi-valilehti*])

       "Välitavoitteet" :valitavoitteet-nakyma
       [valitavoitteet/valitavoitteet ur]]
      [valitavoitteet/valitavoitteet ur])))