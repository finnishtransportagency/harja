(ns harja.views.urakka.laskutusyhteenveto
  "Urakan Laskutusyhteenveto-välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]

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

(defonce laskutusyhteenvedon-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  [alkupvm loppupvm] @u/valittu-hoitokauden-kuukausi
                  nakymassa? @laskutusyhteenveto-nakyvissa?
                  urakkatyyppi (:tyyppi ur)
                  raportin-nimi (if (= :teiden-hoito urakkatyyppi)
                                  :laskutusyhteenveto-mhu
                                  :laskutusyhteenveto)]
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
            tiedot @laskutusyhteenvedon-tiedot
            valittu-aikavali @u/valittu-hoitokauden-kuukausi
            raportin-nimi (if (= :teiden-hoito (:urakkatyyppi ur))
                            :laskutusyhteenveto-mhu
                            :laskutusyhteenveto)]
        [:span.laskutusyhteenveto
         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]
         
         (when-let [p @laskutusyhteenvedon-parametrit]
           [upotettu-raportti/raportin-vientimuodot p])
         
         (if-let [tiedot @laskutusyhteenvedon-tiedot]
           [muodosta-html (assoc-in tiedot [1 :tunniste] raportin-nimi)]
           [yleiset/ajax-loader "Raporttia suoritetaan..."])]))))
