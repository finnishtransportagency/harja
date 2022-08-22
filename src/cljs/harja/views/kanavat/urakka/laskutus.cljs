(ns harja.views.kanavat.urakka.laskutus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.loki :refer [tarkkaile! log]]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja] :as yleiset]
            [harja.ui.debug :refer [debug]]
            [harja.tiedot.navigaatio :as nav]

            [harja.tiedot.urakka :as u]
            [harja.tiedot.raportit :as raportit]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.views.urakka.maksuerat :as maksuerat]
            [harja.ui.bootstrap :as bs]
            [harja.domain.oikeudet :as oikeudet])

  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [reagent.ratom :refer [reaction run!]]
    [harja.atom :refer [reaction<!]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defonce laskutus-nakyvissa? (atom false))

(defonce laskutuksen-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  [alkupvm loppupvm] (or @u/valittu-hoitokauden-kuukausi @u/valittu-hoitokausi)
                  nakymassa? @laskutus-nakyvissa?]
              (when (and ur alkupvm loppupvm nakymassa?)
                (raportit/urakkaraportin-parametrit
                  (:id ur)
                  :kanavien-laskutusyhteenveto
                  {:alkupvm  alkupvm
                   :loppupvm loppupvm})))))


(defonce laskutus-tiedot
  (reaction<! [p @laskutuksen-parametrit]
              {:nil-kun-haku-kaynnissa? true}
              (when p
                (raportit/suorita-raportti p))))


(defn laskutusyhteenveto []
  [:span.laskutusyhteenveto
   [:div.flex-row.alkuun
    [valinnat/urakan-hoitokausi-ja-kuukausi @nav/valittu-urakka]

    (when-let [p @laskutuksen-parametrit]
      [upotettu-raportti/raportin-vientimuodot p])]

   (if-let [tiedot @laskutus-tiedot]
     [muodosta-html (assoc-in tiedot [1 :tunniste] :laskutus-kanavat)]
     [yleiset/ajax-loader "Raporttia suoritetaan..."])])

(defn laskutus
  []
  (komp/luo
    (komp/lippu laskutus-nakyvissa?)
    (fn []
      ;; luettava, jotta reaktiot päivittyvät
      @nav/valittu-urakka
      @laskutus-tiedot
      @u/valittu-hoitokauden-kuukausi

      [:span.laskutus
       [bs/tabs {:style :tabs :classes "tabs-taso2"
                 :active (nav/valittu-valilehti-atom :laskutus)}

        "Laskutusyhteenveto"
        :laskutusyhteenveto
        ^{:key "laskutusyhteenveto"}
        [laskutusyhteenveto]

        "Maksuerät"
        :maksuerat
        ^{:key "maksuerat"}
        (when (oikeudet/urakat-laskutus-maksuerat (:id @nav/valittu-urakka))
          [maksuerat/maksuerat-listaus])]])))
