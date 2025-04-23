(ns harja.views.urakka.valikatselmus.indeksikorjaus
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.dom :as dom]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- laskenta-modaali [paatos]
  (let [pistelukujen-muutos (fmt/desimaaliluku-opt
                              (* 100
                                (/
                                  (-
                                    (js/parseFloat (str/replace (fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1) "," "."))
                                    (js/parseFloat (str/replace (fmt/desimaaliluku-opt (:alkuperainen_pisteluku paatos) 1) "," ".")))
                                  (js/parseFloat (str/replace (fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1) "," "."))))
                              1)]
    [:div
     [:div.flex-row
      [:p.laskenta-rivi  "Hoitovuoden päätyttyä lasketaan hoitovuotta edeltävän syyskuun ja hoitovuoden elokuun välisten kuukausien indeksin pistelukujen keskiarvo. Näin laskettua keskiarvoa verrataan hoitovuotta edeltävän elokuun indeksin pistelukuun. Mikäli muutos (ylitys/alitus) on"
       [:strong " suurempi kuin 2,0 %"] ", korjataan hoitovuoden lopun tavoitehintaa 2,0 %:n ylittävällä %-osuudella. Prosenttiosuus lasketaan 0,1 %:n tarkkuudella."]]
     [:div.flex-row.laskenta-rivi.laskenta-rivi-lukema
      [:div "Pisteluku, johon keskiarvoa verrataan (" (:alkuperaisen_pisteluvun_kuukausi paatos) ")"]
      [:div [:strong (fmt/desimaaliluku-opt (:alkuperainen_pisteluku paatos) 1)]]]
     [:div.flex-row.laskenta-rivi-korkeampi
      [:div "Pistelukujen keskiarvon laskenta"]]
     (for* [kuukausi (:hoitokauden_kuukaudet paatos)]
       [:div.flex-row.kuukausi-rivi
        [:div (str/join " " (reverse (str/split (:kuukausi kuukausi) #"\s+")))]
        [:div (fmt/desimaaliluku-opt (:indeksiluku kuukausi) 1)]])
     (when (not= 12 (count (:hoitokauden_kuukaudet paatos)))
       [yleiset/info-laatikko :vahva-ilmoitus "Kaikkien kuukausien indeksiarvoja ei ole vielä syötetty!" nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}])
     [:hr.hr-tiivis]
     [:div.flex-row.laskenta-rivi
      [:div [:strong "Keskiarvo"]]
      [:div [:strong (fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)]]]
     [:div.flex-row.laskenta-rivi-korkeampi [:div "Indeksikorjauksen prosenttiosuuden laskenta"]]
     [:div.flex-row.laskenta-avattuna
      [:div (str "Pistelukujen muutos 0,1 % tarkkuudella") [:br]
       (str "("(fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)" -"(fmt/desimaaliluku-opt (:alkuperainen_pisteluku paatos) 1)") / "(fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)" * 100")]
      [:div pistelukujen-muutos " %"]]
     [:hr.hr-korkea]
     [:div.flex-row
      [:div [:strong "2,0 % ylittävä osuus"]]
      [:div [:strong (fmt/desimaaliluku-opt (- (js/parseFloat (str/replace pistelukujen-muutos "," ".")) 2) 1)  " %"]]]]))

(defn paatos [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :hoidonjohtopalkkion-muutos
        paatos-tehty? (or (:id paatos) false)

        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Hoitovuoden lopun indeksikorjaus" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        (if-not (:virhe paatos)
          [:div
           [:div.flex-row.summa_rivi_ylin
            [:div "Hoitovuoden lopun indeksikorjattu tavoitehinta"]
            [:div [:strong (fmt/euro-opt false (:tavoitehinta paatos))]]]
           [:div.flex-row.summa_rivi
            [:div "Tavoitehinnan muutokset"]
            [:div [:strong (fmt/euro-opt false true (:tavoitehinnan_muutokset paatos))]]]
           [:div.flex-row.summa_rivi
            [:div "Hoitovuoden lopun tavoitehinta ennen hoitovuoden lopun indeksikorjausta"]
            [:div [:strong (fmt/euro-opt false (:tavoitehinta_ennen paatos))]]]
           [:div.flex-row.summa_rivi
            [:div "Pistelukujen muutos"]
            [:div [:strong (fmt/euro-opt false (:pistelukujen_muutos_prosentteina paatos)) "%"]]]
           [:div.flex-row.summa_rivi
            [:div "Indeksikorjauksen prosenttiosuus (2% ylittävä osa)"]
            [:div [:strong (fmt/euro-opt false (:indeksikorotuksen_prosenttiosuus paatos)) "%"]]]
           [:div.flex-row.linkki_rivi
            [yleiset/linkki "Näytä laskenta"
             (fn [] (modal/nayta! {:otsikko "Laskenta"
                                   :otsikko-muotoilut {:font-size "32px"}
                                   :body-tyyli {:margin-bottom "16px"}
                                   :content-tyyli {:padding-top "24px"}
                                   :footer [napit/sulje "Sulje" #(modal/piilota!)
                                            {:vayla-tyyli? true :luokka "valikatselmus-nappi nappi-toissijainen valikatselmus-nappi-sulje" :ei-ikonia? true}]
                                   :footer-tyyli {:text-align "left"}}
                      [laskenta-modaali paatos]))
             {:style {:text-decoration :underline}}]]
           [:hr]
           [:div.flex-row
            [:h3.ennen-painiketta "Hoitovuoden lopun indeksikorjaus"]
            [:div.otsikko_lukema (fmt/euro-opt false (:hoitokauden_lopun_indeksikorjaus paatos))]]

           [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? voi-muokata?
            #(e! (valikatselmus-tiedot/->TallennaHoitovuodenlopunIndeksikorjauspaatos paatos))
            #(e! (valikatselmus-tiedot/->PoistaHoitovuodenlopunIndeksikorjauspaatos paatos))]]
          [:div.muokkaustoiminnot
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos) nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])])]))
