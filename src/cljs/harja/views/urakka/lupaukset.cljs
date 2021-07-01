(ns harja.views.urakka.lupaukset
  "Lupausten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.lupaukset :as tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.bootstrap :as bs]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.ui.kentat :as kentat])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- pisteympyra
  "Pyöreä nappi, jonka numeroa voi tyypistä riippuen ehkä muokata."
  [tiedot toiminto]
  (assert (#{:ennuste :toteuma :lupaus} (:tyyppi tiedot)) "Tyypin on oltava ennuste, toteuma tai lupaus")
  [:div.inline-block.lupausympyra-container
   [:div {:on-click toiminto
          :style {:cursor (when toiminto
                            "pointer")}
          :class ["lupausympyra" (:tyyppi tiedot)]}
    [:h3 (:pisteet tiedot)]]
   [:div.lupausympyran-tyyppi (name (:tyyppi tiedot))]])

(defn- yhteenveto [e! {:keys [muokkaa-luvattuja-pisteita? lupaus-sitoutuminen] :as app}]
  [:div.lupausten-yhteenveto
   [:div.otsikko-ja-kuukausi
    [:div "Yhteenveto"]
    ;; fixme, oikea kuukausi app statesta
    [:h2.kuukausi "Kesäkuu 2021"]]
   [:div.lupauspisteet
    [pisteympyra {:pisteet 0
                  :tyyppi :ennuste} nil]
    (if muokkaa-luvattuja-pisteita?
      [:div.lupauspisteen-muokkaus-container
       [:div.otsikko "Luvatut pisteet"]
       [kentat/tee-kentta {:tyyppi :positiivinen-numero :kokonaisluku? true}
        (r/wrap (get-in app [:luvatut-pisteet])
                (fn [pisteet]
                  (e! (tiedot/->LuvattujaPisteitaMuokattu pisteet))))]
       [napit/yleinen-ensisijainen "Valmis"
        #(e! (tiedot/->TallennaLupausSitoutuminen))
        {:luokka "lupauspisteet-valmis"}]]
      [pisteympyra (merge lupaus-sitoutuminen
                          {:tyyppi :lupaus})
       #(e! (tiedot/->VaihdaLuvattujenPisteidenMuokkausTila))])]])

(defn- ennuste [e! app]
  [:div.lupausten-ennuste
   [:div "Ennusteen mukaan urakalle on tulossa sanktiota... (ominaisuus tekemättä)"]])

(defn lupaukset-alempi-valilehti*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (tiedot/->HaeUrakanLupaustiedot (-> @tila/yleiset :urakka :id)))))
    (komp/ulos #(e! (tiedot/->NakymastaPoistuttiin)))
    (fn [e! app]
      [:span.lupaukset-sivu
       [:h1 "Lupaukset"]
       [yhteenveto e! app]
       [debug app {:otsikko "TUCK STATE"}]
       [ennuste e! app]])))

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