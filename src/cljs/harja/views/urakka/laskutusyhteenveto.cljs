(ns harja.views.urakka.laskutusyhteenveto
  "Urakan Laskutusyhteenveto-välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]

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
  [["Työmaakokous" :tyomaakokous]
   ["Tuotekohtainen" :tuotekohtainen]])

(defonce laskutusyhteenvedon-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  [alkupvm loppupvm] @u/valittu-hoitokauden-kuukausi
                  nakymassa? @laskutusyhteenveto-nakyvissa?
                  urakkatyyppi (:tyyppi ur)

                  ;; _ (println "urakkatyyppi: " urakkatyyppi "valittu-yhteenveto-muoto:" @valittu-yhteenveto-muoto)

                  raportin-nimi (cond (and
                                       (= :teiden-hoito urakkatyyppi)
                                       (= :tuotekohtainen @valittu-yhteenveto-muoto)) :laskutusyhteenveto-mhu

                                      (and
                                       (= :teiden-hoito urakkatyyppi)
                                       (= :tyomaakokous @valittu-yhteenveto-muoto)) :laskutusyhteenveto-tyomaa

                                      (not= :teiden-hoito urakkatyyppi) :laskutusyhteenveto)]
              
              (when (and ur alkupvm loppupvm nakymassa?)
                (raportit/urakkaraportin-parametrit
                 (:id ur)
                 raportin-nimi
                 {:alkupvm alkupvm
                  :loppupvm loppupvm
                  :urakkatyyppi urakkatyyppi})))))

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
           ;; valittu-aikavali @u/valittu-hoitokauden-kuukausi
           ;;_ (println "urakkatyyppi: " (:urakkatyyppi ur) "valittu-yhteenveto-muoto:" @valittu-yhteenveto-muoto)
           raportin-nimi (cond (and
                                (= :teiden-hoito (:urakkatyyppi ur))
                                (= :tuotekohtainen @valittu-yhteenveto-muoto)) :laskutusyhteenveto-mhu

                               (and
                                (= :teiden-hoito (:urakkatyyppi ur))
                                (= :tyomaakokous @valittu-yhteenveto-muoto)) :laskutusyhteenveto-tyomaa

                               (not= :teiden-hoito (:urakkatyyppi ur)) :laskutusyhteenveto)]

       [:span.laskutusyhteenveto
        [:div.flex-row.alkuun
         (println "Calling.." (:tyyppi ur))
         (when (= :teiden-hoito (:tyyppi ur))
           (println "True")
           [:div "Laskutusyhteenvedon muoto"
            [:div
             [kentat/tee-kentta {:tyyppi :radio
                                 :valinta-nayta first
                                 :valinta-arvo second
                                 :valinnat yhteenvedeon-valinnat}
              valittu-yhteenveto-muoto]]])

         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]

         (when-let [p @laskutusyhteenvedon-parametrit]
           [upotettu-raportti/raportin-vientimuodot p])]


        (if-let [tiedot @laskutusyhteenvedon-tiedot]
          [muodosta-html
           (-> tiedot
               (assoc-in [1 :tunniste] raportin-nimi)
               (assoc-in [1 :yhteenvetotyyppi] @valittu-yhteenveto-muoto))]
          [yleiset/ajax-loader "Raporttia suoritetaan..."])]))))
