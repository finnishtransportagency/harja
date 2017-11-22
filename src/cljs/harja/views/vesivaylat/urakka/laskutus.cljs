(ns harja.views.vesivaylat.urakka.laskutus
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]

            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.raportit :as raportit]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.ui.yleiset :as yleiset])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction run!]]))

(defonce laskutus-nakyvissa? (atom false))
(def sivu "Vesiväylät/Laskutus")

(defonce laskutuksen-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  [alkupvm loppupvm] (or @u/valittu-hoitokauden-kuukausi @u/valittu-hoitokausi)
                  nakymassa? @laskutus-nakyvissa?]
              (when (and ur alkupvm loppupvm nakymassa?)
                (raportit/urakkaraportin-parametrit
                  (:id ur)
                  :vesivaylien-laskutusyhteenveto
                  {:alkupvm  alkupvm
                   :loppupvm loppupvm})))))


(defonce laskutus-tiedot
  (reaction<! [p @laskutuksen-parametrit]
              {:nil-kun-haku-kaynnissa? true}
              (when p
                (raportit/suorita-raportti p))))


(defn laskutus
  []
  (komp/luo
    (komp/kirjaa-kaytto! sivu)
    (komp/lippu laskutus-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            tiedot @laskutus-tiedot
            valittu-aikavali @u/valittu-hoitokauden-kuukausi]
        [:span.laskutusyhteenveto
         [valinnat/urakan-hoitokausi-ja-kuukausi ur]

         (when-let [p @laskutuksen-parametrit]
           [upotettu-raportti/raportin-vientimuodot p])

         (if-let [tiedot @laskutus-tiedot]
           [muodosta-html (assoc-in tiedot [1 :tunniste] :laskutus-vesivaylat)]
           [yleiset/ajax-loader "Raporttia suoritetaan..."])]))))
