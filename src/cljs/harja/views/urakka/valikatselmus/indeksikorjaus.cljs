(ns harja.views.urakka.valikatselmus.indeksikorjaus
  (:require [reagent.core :as r :refer [atom]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.urakka :as urakka]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.dom :as dom]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as kulut-yhteiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- laskenta-modaali [paatos]
  [:div
   [:div.flex-row
    [:p "Hoitovuoden päätyttyä lasketaan hoitovuotta edeltävän syyskuun ja hoitovuoden elokuun välisten kuukausien indeksin pistelukujen keskiarvo. Näin laskettua keskiarvoa verrataan hoitovuotta edeltävän elokuun indeksin pistelukuun. Mikäli muutos (ylitys/alitus) on"
     [:strong " suurempi kuin 2,0 %"] ", korjataan hoitovuoven lopun tavoitehintaa 2,0 %:n ylittävällä %-osuudella. Prosenttiosuus lasketaan 0,1 %:n tarkkuudella."]]
   [:div.flex-row
    [:div [:strong "Pisteluku, johon keskiarvoa verrataan (" (:alkuperaisen_pisteluvun_kuukausi paatos) ")"]]
    [:div [:strong (:alkuperainen_pisteluku paatos)]]]
   [:div.flex-row
    [:div [:strong "Pistelukujen keskiarvon laskenta"]]]
   (for* [kuukausi (:hoitokauden_kuukaudet paatos)]
     [:div.flex-row
      [:div (:kuukausi kuukausi)]
      [:div [:strong (fmt/desimaaliluku-opt (:indeksiluku kuukausi) 1)]]])
   (when (not= 12 (count (:hoitokauden_kuukaudet paatos)))
     [yleiset/info-laatikko :vahva-ilmoitus "Kaikkien kuukausien indeksiarvoja ei ole vielä syötetty!"])
   [:div.flex-row
    [:div [:strong "Keskiarvo"]]
    [:div [:strong (fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)]]]
   [:div.flex-row [:div [:strong "Indeksikorjauksen prosenttiosuuden laskenta"]]]
   [:div.flex-row [:div (str "Pistelukujen muutos 0,1 % tarkkuudella ("(fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)" -
   "(:alkuperainen_pisteluku paatos)") / "(fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)" * 100")]]
   [:div.flex-row
    [:div [:strong "2,0 % ylittävä osuus"]]
    [:div [:strong (fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)]]]])

(defn paatos [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
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
           [:div.flex-row
            [:div "Hoitovuoden lopun indeksikorjattu tavoitehinta"]
            [:div [:strong (fmt/euro-opt false (:tavoitehinta paatos))]]]
           [:div.flex-row
            [:div "Tavoitehinnan muutokset"]
            [:div [:strong (fmt/euro-opt false (:tavoitehinnan_muutokset paatos))]]]
           [:div.flex-row
            [:div "Hoitovuoden lopun tavoitehinta ennen hoitovuoden lopun indeksikorjausta"]
            [:div [:strong (fmt/euro-opt false (:tavoitehinta_ennen paatos))]]]
           [:div.flex-row
            [:div "Pistelukujen muutos"]
            [:div [:strong (fmt/euro-opt false (:pistelukujen_muutos paatos)) "%"]]]
           [:div.flex-row
            [:div "Indeksikorjauksen prosenttiosuus (2% ylittävä osa)"]
            [:div [:strong (fmt/euro-opt false (:indeksikorotuksen_prosenttiosuus paatos)) "%"]]]
           [:div.flex-row
            [yleiset/linkki "Näytä laskenta"
             (fn [] (modal/nayta! {:otsikko "Laskenta"
                                   :footer [napit/sulje #(modal/piilota!)]}
                      [laskenta-modaali paatos]))]]
           [:div.flex-row
            [:div "Hoitovuoden lopun indeksikorjaus"]
            [:div [:strong (fmt/euro-opt false (:hoitokauden_lopun_indeksikorjaus paatos)) "%"]]]

           [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? voi-muokata?
            #(e! (valikatselmus-tiedot/->TallennaHoitovuodenlopunIndeksikorjauspaatos paatos))
            #(e! (valikatselmus-tiedot/->PoistaHoitovuodenlopunIndeksikorjauspaatos paatos))]]
          [:div {:style {:padding-bottom "1rem"}}
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos)]])])]))
