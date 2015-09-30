(ns harja.views.urakka.laskutusyhteenveto
  "Urakan Laskutusyhteenveto välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            
            [harja.tiedot.raportit :as raportit]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.raportti :refer [muodosta-html]] 
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defonce laskutusyhteenveto-nakyvissa? (atom false))

(defonce laskutusyhteenvedon-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  [hk-alkupvm hk-loppupvm] @u/valittu-hoitokausi
                  [aikavali-alkupvm aikavali-loppupvm] @u/valittu-hoitokauden-kuukausi
                  nakymassa? @laskutusyhteenveto-nakyvissa?]
              (when (and ur hk-alkupvm hk-loppupvm
                         aikavali-alkupvm aikavali-loppupvm
                         nakymassa?)
                (raportit/suorita-raportti-urakka-parametrit
                 (:id ur)
                 :laskutusyhteenveto
                 {:hk-alkupvm        hk-alkupvm
                  :hk-loppupvm       hk-loppupvm
                  :aikavali-alkupvm  aikavali-alkupvm
                  :aikavali-loppupvm aikavali-loppupvm})))))


(defonce laskutusyhteenvedon-tiedot
  (reaction<! [p @laskutusyhteenvedon-parametrit]
              (when p
                (raportit/suorita-raportti p))))


(defn laskutusyhteenveto
  []
  (komp/luo
    (komp/lippu laskutusyhteenveto-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            tiedot @laskutusyhteenvedon-tiedot
            valittu-aikavali @u/valittu-hoitokauden-kuukausi]
        [:span.laskutusyhteenveto
         [:h3 "Laskutusyhteenveto"]
         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]
         
         (when-let [p @laskutusyhteenvedon-parametrit]
           [:form {:style {:float "right"} :target "_blank" :method "POST"
                   
                   :action (k/pdf-url :raportointi)}
            [:input {:type "hidden" :name "parametrit"
                     :value (t/clj->transit p)}]
            [:button.nappi-ensisijainen {:type "submit"}
             (ikonit/print)
             " Lataa PDF"]])
         
         (when-let [tiedot @laskutusyhteenvedon-tiedot]
           [muodosta-html tiedot])]))))
