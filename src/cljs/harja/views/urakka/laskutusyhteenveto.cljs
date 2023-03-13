(ns harja.views.urakka.laskutusyhteenveto
  "Urakan Laskutusyhteenveto-välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.pvm :as pvm]
            [harja.tiedot.raportit :as raportit]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.ui.yleiset :as yleiset])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defonce laskutusyhteenveto-nakyvissa? (atom false))

(def valittu-yhteenveto-muoto (atom :tyomaakokous))

(defonce yhteenvedeon-valinnat
  {:tyomaakokous "Työmaakokous"
   :tuotekohtainen "Tuotekohtainen"})

(def valittu-yhteenveto-aikarajaus (atom :hoitokausi))

(defonce aikarajaus-valinnat
  {:hoitokausi "Hoitokauden mukaan"
   :kalenterivuosi "Kalenterivuoden mukaan"
   :valittu-aikakvali "Valittu aikaväli"})

(defn raportin-nimi-avain [urakkatyyppi]
  (cond (and
         ;; MHU / HJU -urakoille näytetään työmaakokous ja tuotekohtainen yhteenveto
         ;; Tuotekohtainen
         (= :teiden-hoito urakkatyyppi)
         (= :tuotekohtainen @valittu-yhteenveto-muoto)) :laskutusyhteenveto-tuotekohtainen

        (and
         ;; Työmaakokous
         (= :teiden-hoito urakkatyyppi)
         (= :tyomaakokous @valittu-yhteenveto-muoto)) :laskutusyhteenveto-tyomaa

        ;; Muille urakoille näytetään "perus" yhteenveto
        (not= :teiden-hoito urakkatyyppi) :laskutusyhteenveto))

(defonce laskutusyhteenvedon-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  [alkupvm loppupvm] @u/valittu-hoitokauden-kuukausi
                  nakymassa? @laskutusyhteenveto-nakyvissa?
                  raportin-nimi (raportin-nimi-avain (:tyyppi ur))]
              
              (when (and ur alkupvm loppupvm nakymassa?)
                (raportit/urakkaraportin-parametrit
                 (:id ur)
                 raportin-nimi
                 {:alkupvm alkupvm
                  :loppupvm loppupvm
                  :urakkatyyppi (:tyyppi ur)})))))

(defonce laskutusyhteenvedon-tiedot
  (reaction<! [p @laskutusyhteenvedon-parametrit]
              {:nil-kun-haku-kaynnissa? true}
              (when p
                (raportit/suorita-raportti p))))

(defn laskutusyhteenveto
  []
  (komp/luo
   (komp/lippu laskutusyhteenveto-nakyvissa?)
   (fn []
     (let [ur @nav/valittu-urakka
           raportin-nimi (raportin-nimi-avain (:tyyppi ur))]

       [:span.laskutusyhteenveto
        [:div.flex-row.alkuun 
         
         ;; MHU / HJU -urakoille näytetään valinnat työmaakokous & tuotekohtainen yhteenveto
         (when (= :teiden-hoito (:tyyppi ur))
           [:div {:class "mhu-radio"}
           [:div {:class "laskutus-yhteensa" :style {:font-weight "normal" :margin-top "20px"}} "Laskutusyhteenvedon muoto"
            [:div {:style {:margin-right "60px" :margin-top "-10px" :margin-bottom "40px"}}

             [kentat/tee-kentta {:tyyppi :radio-group
                                 :space-valissa? true
                                 :vaihtoehdot [:tyomaakokous :tuotekohtainen]
                                 :vayla-tyyli? true
                                 :nayta-rivina? true
                                 :vaihtoehto-nayta yhteenvedeon-valinnat}
              valittu-yhteenveto-muoto]]]

             [:div {:class "laskutus-yhteensa" :style {:font-weight "normal" :margin-top "20px"}} "Aikarajaus"

               [kentat/tee-kentta {:tyyppi :radio-group
                                   :vaihtoehdot [:hoitokausi :kalenterivuosi :valittu-aikakvali]
                                   :vayla-tyyli? true
                                   :nayta-rivina? false
                                   :vaihtoehto-nayta aikarajaus-valinnat}
                valittu-yhteenveto-aikarajaus]]]
           )
         
         [valinnat/urakan-hoitokausi ur]
         
         [valinnat/hoitokauden-kuukausi
          (pvm/aikavalin-kuukausivalit @u/valittu-hoitokausi)
          u/valittu-hoitokauden-kuukausi
          u/valitse-hoitokauden-kuukausi!
          "Kuukausi"]

         (when-let [p @laskutusyhteenvedon-parametrit]
           [upotettu-raportti/raportin-vientimuodot p])]


        (if-let [tiedot @laskutusyhteenvedon-tiedot]
          [muodosta-html
           (-> tiedot
               (assoc-in [1 :tunniste] raportin-nimi)
               (assoc-in [1 :yhteenvetotyyppi] @valittu-yhteenveto-muoto))]
          [yleiset/ajax-loader "Raporttia suoritetaan..."])]))))
