(ns harja.views.urakka.laskutusyhteenveto
  "Urakan Laskutusyhteenveto-välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [vihje]]
            [harja.ui.kentat :as kentat]
            [harja.pvm :as pvm]
            [harja.tiedot.raportit :as raportit]
            [harja.views.raportit :as raportit-ui]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.ui.yleiset :as yleiset])
  
  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(def valittu-vuosi (reaction-writable
                    (pvm/vuosi (pvm/nyt))))

(defonce laskutusyhteenveto-nakyvissa? (atom false))
(defonce vapaa-aikavali? (atom false))
(defonce vapaa-aikavali (atom [nil nil]))
(defonce valittu-kuukausi (reaction-writable @u/valittu-hoitokauden-kuukausi))

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

                  alkupvm (cond
                            ;; Jos ""koko hoitokausi"" on valittuna, käytetään valitun hoitokauden päivämääriä
                            (= @valittu-yhteenveto-aikarajaus :hoitokausi)
                            (if (nil? alkupvm) (first @u/valittu-hoitokausi) alkupvm)

                            (= @valittu-yhteenveto-aikarajaus :kalenterivuosi)
                            (if @valittu-kuukausi 
                              (first @valittu-kuukausi)
                              (pvm/vuoden-eka-pvm @valittu-vuosi))

                            :else (first @vapaa-aikavali))

                  loppupvm (cond
                             ;; Jos ""koko hoitokausi"" on valittuna, käytetään valitun hoitokauden päivämääriä
                             (= @valittu-yhteenveto-aikarajaus :hoitokausi)
                             (if (nil? loppupvm) (second @u/valittu-hoitokausi) loppupvm)

                             (= @valittu-yhteenveto-aikarajaus :kalenterivuosi)
                             (if @valittu-kuukausi
                               (second @valittu-kuukausi)
                               (pvm/vuoden-viim-pvm @valittu-vuosi))

                             :else (second @vapaa-aikavali))

                  nakymassa? @laskutusyhteenveto-nakyvissa?
                  raportin-nimi (raportin-nimi-avain (:tyyppi ur))]
              
              (when (and ur alkupvm loppupvm nakymassa?)
                (raportit/urakkaraportin-parametrit
                 (:id ur)
                 raportin-nimi
                 {:alkupvm alkupvm
                  :loppupvm loppupvm
                  :valittu-kk @valittu-kuukausi
                  :aikarajaus @valittu-yhteenveto-aikarajaus
                  :urakkatyyppi (:tyyppi ur)})))))

(defonce laskutusyhteenvedon-tiedot
  (reaction<! [p @laskutusyhteenvedon-parametrit]
              {:nil-kun-haku-kaynnissa? true}
              (when p
                (raportit/suorita-raportti p))))

(defonce kuukaudet (reaction-writable
                    (let [hk @u/valittu-hoitokausi
                          vuosi @valittu-vuosi]
                      (into [] (concat [nil] (cond
                                               hk (pvm/aikavalin-kuukausivalit hk)
                                               vuosi (pvm/vuoden-kuukausivalit vuosi)
                                               :else []))))))

(defn suorita-raportti [raportin-nimi]
  (if-let [tiedot @laskutusyhteenvedon-tiedot]
    [muodosta-html
     (-> tiedot
         (assoc-in [1 :tunniste] raportin-nimi)
         (assoc-in [1 :yhteenvetotyyppi] @valittu-yhteenveto-muoto))]
    [yleiset/ajax-loader "Raporttia suoritetaan..."]))

(defn laskutusyhteenveto
  []
  (komp/luo
   (komp/lippu laskutusyhteenveto-nakyvissa?)
   (fn []
     (let [ur @nav/valittu-urakka
           vuosi-eka (if ur (pvm/vuosi (:alkupvm ur)) 2010)
           vuosi-vika (if ur (pvm/vuosi (:loppupvm ur)) (pvm/vuosi (pvm/nyt)))
           raportin-nimi (raportin-nimi-avain (:tyyppi ur))]

       [:span.laskutusyhteenveto
        [:div.flex-row.alkuun

         ;; MHU / HJU -urakoille näytetään valinnat työmaakokous & tuotekohtainen yhteenveto
         (when (= :teiden-hoito (:tyyppi ur))
           [:div {:class "mhu-radio"}
            [:div {:class "laskutus-yhteensa"} "Laskutusyhteenvedon muoto"
             [:div {:class "kentta"}

              [kentat/tee-kentta {:tyyppi :radio-group
                                  :space-valissa? true
                                  :vaihtoehdot [:tyomaakokous :tuotekohtainen]
                                  :vayla-tyyli? true
                                  :nayta-rivina? true
                                  :valitse-fn #(do
                                                 (reset! u/valittu-hoitokauden-kuukausi nil)
                                                 (reset! valittu-yhteenveto-aikarajaus :hoitokausi))
                                  :vaihtoehto-nayta yhteenvedeon-valinnat}
               valittu-yhteenveto-muoto]]]

            [:div {:class "laskutus-yhteensa"} "Aikarajaus"
             [:div {:class "kentta"}

             [kentat/tee-kentta {:tyyppi :radio-group
                                 :vaihtoehdot (cond 
                                                ;; Työmaakokoukselle ei anneta vuosivalintaa
                                                (= @valittu-yhteenveto-muoto :tyomaakokous) [:hoitokausi :valittu-aikakvali]
                                                :else [:hoitokausi :kalenterivuosi :valittu-aikakvali])
                                 :vayla-tyyli? true
                                 :nayta-rivina? false
                                 ;; Kun vaihdetaan yhteenvedon muotoa resetoidaan kalenteri arvoja
                                 :valitse-fn #(do
                                                (reset! u/valittu-hoitokauden-kuukausi nil)
                                                (reset! u/valittu-hoitokausi nil)
                                                (reset! kuukaudet (pvm/vuoden-kuukausivalit (pvm/vuosi (pvm/nyt))))
                                                (reset! valittu-vuosi (pvm/vuosi (pvm/nyt))))
                                 :vaihtoehto-nayta aikarajaus-valinnat}
              valittu-yhteenveto-aikarajaus]]]])

         (cond
           ;; Hoitokausi valittuna
           (= @valittu-yhteenveto-aikarajaus :hoitokausi)
           [:div
            [valinnat/urakan-hoitokausi ur]
            [ui-valinnat/kuukausi {:disabled @vapaa-aikavali?
                                   :nil-valinta "Koko hoitokausi"
                                   :disabloi-tulevat-kk? true}
             @kuukaudet u/valittu-hoitokauden-kuukausi]]

           ;; Tietty vuosi valittuna
           (= @valittu-yhteenveto-aikarajaus :kalenterivuosi)
           [:div
            [ui-valinnat/vuosi {:disabled false}
             vuosi-eka vuosi-vika valittu-vuosi
             #(do
                (reset! valittu-vuosi %)
                (reset! u/valittu-hoitokauden-kuukausi nil)
                (reset! u/valittu-hoitokausi nil)
                (reset! valittu-kuukausi nil))]

            [ui-valinnat/kuukausi {:disabled @vapaa-aikavali?
                                   :nil-valinta "Koko vuosi"
                                   :disabloi-tulevat-kk? true}
             @kuukaudet u/valittu-hoitokauden-kuukausi]]

           ;; Valittuna kustomi aikaväli
           :else
           [:div
            [ui-valinnat/aikavali vapaa-aikavali {:aikavalin-rajoitus [raportit-ui/+raportin-aikavalin-max-pituus-vuotta+ :vuosi]
                                                  :validointi :korkeintaan-kuluva-paiva}]
            ;; Käytetään täälläkin rajaa samoin kun raporttien puolella, niin ei mene queryt tukkoon
            [vihje (str "Raportin pisin sallittu aikaväli on " raportit-ui/+raportin-aikavalin-max-pituus-vuotta+ " vuotta") "raportit-valittuaikavali-vihje"]])

         (when-let [p @laskutusyhteenvedon-parametrit]
           [upotettu-raportti/raportin-vientimuodot p])]

        ;; Jos hoitokautta ei ole valittuna, näytä viesti
        (if (and (= @valittu-yhteenveto-aikarajaus :hoitokausi) (nil? @u/valittu-hoitokausi))
          [:div "Valitse hoitokausi"]

          (if (= @valittu-yhteenveto-aikarajaus :valittu-aikakvali)
            
            ;; Jos käytetään kustomi aikaväliä, katsotaan että molemmat arvot ovat olemassa
            (let [alku (first @vapaa-aikavali)
                  loppu (second @vapaa-aikavali)]

              (if (and alku loppu)
                (suorita-raportti raportin-nimi)
                [:div "Valitse aikaväli"]))

            (suorita-raportti raportin-nimi)))]))))
