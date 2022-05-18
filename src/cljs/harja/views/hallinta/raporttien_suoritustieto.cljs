(ns harja.views.hallinta.raporttien-suoritustieto

  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.domain.raportointi :as raportti-domain]
            [harja.tiedot.hallinta.raporttien-suoritustieto :as tiedot]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as y]
            [harja.ui.kentat :as kentat]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.visualisointi :as vis]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))



(defn- roolivalinta [valittu-atom valinnat]
  [kentat/tee-otsikollinen-kentta
   {:otsikko "Tekijän rooli"
    :kentta-params {:tyyppi :valinta :valinnat valinnat
                    :valinta-arvo first :valinta-nayta second}
    :arvo-atom valittu-atom}])

(defn- formaattivalinta [valittu-atom valinnat]
  [kentat/tee-otsikollinen-kentta
   {:otsikko "Formaatti"
    :kentta-params {:tyyppi :valinta :valinnat valinnat
                    :valinta-arvo first :valinta-nayta second}
    :arvo-atom valittu-atom}])

(defn- raporttivalinta [valittu-atom valinnat]
  [kentat/tee-otsikollinen-kentta
   {:otsikko "Raportti"
    :kentta-params {:tyyppi :valinta :valinnat valinnat
                    :valinta-arvo first :valinta-nayta second}
    :arvo-atom valittu-atom}])

(defn- raporttitieto-grid [rivit kaikki-yhteensa-lkm]
  (let [rivit (if-not @tiedot/valittu-raportti
                (conj rivit
                      {:count kaikki-yhteensa-lkm
                       :raportti "Suorituksia yhteensä"
                       :lihavoi true})
                rivit)]
    [:div.raporttitietojen-grid
     [grid/grid
      {:otsikko (str "Raporttien suoritustiedot " (pvm/pvm-opt (first @yhteiset/valittu-aikavali)) "-"
                     (pvm/pvm-opt (second @yhteiset/valittu-aikavali)))
       :tyhja (if (nil? rivit) [yleiset/ajax-loader "Tietoja haetaan..."] "Ei tietoja")
       :tunniste :raportti
       :piilota-toiminnot? true}
      [{:otsikko "Raportti" :nimi :raportti :tyyppi :string :leveys 6
        :fmt (fn [arvo]
               (if (= arvo "Suorituksia yhteensä")
                 arvo
                 (tiedot/raportin-nimi-fmt tiedot/+mahdolliset-raportit+ arvo)))}
       {:otsikko "Suoritus\u00ADkertoja" :nimi :count :tyyppi :string :leveys 2
        :tasaa :oikea}]
      rivit]]))

(defn raporttitieto-top5-piirakka [avain-arvo-map]
  (when-not (empty? avain-arvo-map)
    [:div.piirakka-wrapper
     [:h5.piirakka-label "TOP 5 raportit"]
     [vis/pie
      {:width 460 :height 300 :radius 120 :show-text :percent :show-legend true}
      avain-arvo-map]]))

(defn pylvaat-rooleittain [rivit raportti]
  [:div.pylvaat
   [:h3 (str "Suoritukset rooleittain "
             (if raportti
               (tiedot/raportin-nimi-fmt tiedot/+mahdolliset-raportit+
                                         raportti)
               " kaikki raportit"))]
   [vis/bars {:width 1000
              :height 200
              :format-amount str
              :hide-value? false}
    rivit]
   [yleiset/vihje "Näytetään tärkeimmät roolit - on muitakin rooleja joilla tehdään raportteja. Osalla käyttäjistä on useita eri rooleja ja yksi raportti näkyy tällöin useassa pylväässä."]])

(defn raporttien-suoritustieto [_]
  (komp/luo
    (komp/sisaan-ulos #(reset! tiedot/nakymassa? true)
                      #(reset! tiedot/nakymassa? false))
    (fn [_]
      (let [{:keys [rivit rooleittain kaikki-yhteensa-lkm]} @tiedot/raporttitiedot
            top-5 (take 5 rivit)
            piirakan-data (into {}
                                (map (fn [{:keys [count raportti]}]
                                       {(tiedot/raportin-nimi-fmt tiedot/+mahdolliset-raportit+
                                                                  raportti) count})
                                     top-5))]
        [:span.raporttitieto
         [valinnat/aikavali yhteiset/valittu-aikavali]
         [raporttivalinta tiedot/valittu-raportti tiedot/+mahdolliset-raportit+]
         [roolivalinta tiedot/valittu-rooli raportti-domain/+mahdolliset-roolit+]
         [formaattivalinta tiedot/valittu-formaatti tiedot/+mahdolliset-formaatit+]
         [yleiset/vihje "Tilastotiedon kerääminen aloitettu 1.10.2021."]
         [:div.flex
          [raporttitieto-grid rivit kaikki-yhteensa-lkm]
          [:div.sarake
           (when (and rooleittain (not @tiedot/valittu-rooli))
             [pylvaat-rooleittain rooleittain @tiedot/valittu-raportti])
           (when-not @tiedot/valittu-raportti
             [raporttitieto-top5-piirakka piirakan-data])]]]))))