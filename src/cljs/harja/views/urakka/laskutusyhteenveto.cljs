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
            [harja.asiakas.kommunikaatio :as k])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defonce laskutusyhteenveto-nakyvissa? (atom false))

(defonce laskutusyhteenvedon-tiedot (reaction<! [ur @nav/valittu-urakka
                                                 [hk-alkupvm hk-loppupvm] @u/valittu-hoitokausi
                                                 [aikavali-alkupvm aikavali-loppupvm] @u/valittu-hoitokauden-kuukausi
                                                 nakymassa? @laskutusyhteenveto-nakyvissa?]
                                                ;urakka-id hk_alkupvm hk_loppupvm aikavali-alkupvm aikavali-loppupvm
                                                (when (and ur hk-alkupvm hk-loppupvm
                                                           aikavali-alkupvm aikavali-loppupvm
                                                           nakymassa?)
                                                  (raportit/suorita-raportti-urakka (:id ur)
                                                                                    :laskutusyhteenveto
                                                                                    {:hk-alkupvm        hk-alkupvm
                                                                                     :hk-loppupvm       hk-loppupvm
                                                                                     :aikavali-alkupvm  aikavali-alkupvm
                                                                                     :aikavali-loppupvm aikavali-loppupvm}))))


(defn laskutusyhteenveto
  []
  (komp/luo
    (komp/lippu laskutusyhteenveto-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            tiedot @laskutusyhteenvedon-tiedot
            talvihoidon-tiedot (filter #(= (:tuotekoodi %) "23100") tiedot)
            valittu-aikavali @u/valittu-hoitokauden-kuukausi]
        [:span.laskutusyhteenveto
         [:h3 "Laskutusyhteenveto"]
         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]
         
         (when-let [kk @u/valittu-hoitokauden-kuukausi]
           [:form {:style {:float "right"} :target "_blank" :method "GET"
                   :action (k/pdf-url :laskutusyhteenveto)}
            [:input {:type "hidden" :name "_" :value "laskutusyhteenveto"}]
            [:input {:type "hidden" :name "u" :value (:id ur)}]
            [:input {:type "hidden" :name "vuosi" :value (pvm/vuosi (first kk))}]
            [:input {:type "hidden" :name "kk" :value (pvm/kuukausi (first kk))}]
            [:button.nappi-ensisijainen {:type "submit"}
             (ikonit/print)
             " Lataa PDF"]])
         
         (when-let [tiedot @laskutusyhteenvedon-tiedot]
           (log "LASK YHT TIEDOT: " (pr-str tiedot))
           [muodosta-html tiedot])]))))
