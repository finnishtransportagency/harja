(ns harja.views.urakka.valikatselmus.indeksikorjaus
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- laskenta-modaali [paatos]
  (let [keskiarvo (js/parseFloat (str/replace (fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1) "," "."))
        alkuperainen (js/parseFloat (str/replace (fmt/desimaaliluku-opt (:alkuperainen_pisteluku paatos) 1) "," "."))]
    [:div
     [:div.flex-row
      [:p.laskenta-rivi "Hoitovuoden päätyttyä lasketaan hoitovuotta edeltävän syyskuun ja hoitovuoden elokuun välisten
      kuukausien indeksin pistelukujen keskiarvo. Näin laskettua keskiarvoa verrataan hoitovuotta edeltävän elokuun
      indeksin pistelukuun. Mikäli muutos (ylitys/alitus) on"
       [:strong " suurempi kuin 2,0 %"] ", korjataan hoitovuoden lopun tavoitehintaa 2,0 %:n ylittävällä %-osuudella.
       Prosenttiosuus lasketaan 0,1 %:n tarkkuudella."]]
     [:div.flex-row.laskenta-rivi.laskenta-rivi-lukema
      [:div "Pisteluku, johon keskiarvoa verrataan (" (:alkuperaisen_pisteluvun_kuukausi paatos) ")"]
      [:div [:strong (fmt/desimaaliluku-opt (:alkuperainen_pisteluku paatos) 1)]]]

     [:div.flex-row.laskenta-rivi-korkeampi
      [:div "Pistelukujen keskiarvon laskenta"]]

     (for* [kuukausi (:hoitokauden_kuukaudet paatos)]
       [:div.flex-row.kuukausi-rivi
        [:div (str/join " " (reverse (str/split (:kuukausi kuukausi) #"\s+")))]
        [:div (fmt/desimaaliluku-opt (:indeksiluku kuukausi) 1)]])

     ;; Jos kaikkia indeksikuukausia ei ole vielä hallintaan lisätty, niin näytetään nolla arvoilla puuttuvat kuukaudet
     (when (> (count (:puuttuvat_kuukaudet paatos)) 0)
       (for* [kuukausi (:puuttuvat_kuukaudet paatos)]
         [:div.flex-row.kuukausi-rivi
          [:div (str/join " " (reverse (str/split (:kuukausi kuukausi) #"\s+")))]
          [:div (fmt/desimaaliluku-opt (:indeksiluku kuukausi) 1)]]))

     (when (not= 12 (count (:hoitokauden_kuukaudet paatos)))
       [yleiset/info-laatikko :vahva-ilmoitus
       "Kaikkien kuukausien indeksin pistelukua ei ole vielä saatavilla."
        nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}])

     [:hr.hr-tiivis]

     [:div.flex-row.laskenta-rivi
      [:div [:strong "Keskiarvo"]]
      [:div [:strong (fmt/desimaaliluku-opt (:kuukausien_keskiarvo paatos) 1)]]]

     [:div.flex-row.laskenta-rivi-korkeampi [:div "Indeksikorjauksen prosenttiosuuden laskenta"]]

     [:div.flex-row.laskenta-avattuna
      [:div (str "Pistelukujen muutos 0,1 % tarkkuudella") [:br]
       [:span "("]
       (for [k (take (min (count (:hoitokauden_kuukaudet paatos)) 5) (:hoitokauden_kuukaudet paatos))]
         ^{:key (str "indeksiluku-" (gensym))}
         [:span (fmt/desimaaliluku-opt (:indeksiluku k) 1) " + "])
       (str " ... / " (count (:hoitokauden_kuukaudet paatos)) ")")
       (str " / " (fmt/desimaaliluku-opt (:alkuperainen_pisteluku paatos) 1) " * 100")]
      [:div (:pistelukujen_muutos_prosentteina paatos) " %"]]]))

(defn paatos [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :indeksikorjaus
        paatos-tehty? (some? (:id paatos))

        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-reunuksella
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Hoitovuoden lopun indeksikorjaus" paatos-tehty? paatos-avain avatut-paatokset
      (partial valikatselmus-tiedot/avaa-tai-sulje-haitari) (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        (if-not (:virhe paatos)
          [:div
           [:div.flex-row.lista-rivi
            [:div "Hoitovuoden alun indeksikorjattu tavoitehinta"]
            [:div [:strong (fmt/euro-opt false (:hv_alun_indkorj_tavoitehinta paatos))]]]

           [:div.flex-row.lista-rivi-korkeampi
            [:div "Tavoitehinnan muutokset"]
            [:div [:strong (fmt/euro-opt false true (:tavoitehinnan_muutokset paatos))]]]

           [:div.flex-row.lista-rivi-korkeampi
            [:div "Hoitovuoden lopun tavoitehinta ennen indeksikorjausta"]
            [:div [:strong (fmt/euro-opt false (:hv_lopun_tavoitehinta_ennen_indkorj paatos))]]]

           [:div.flex-row.lista-rivi-korkeampi
            [:div "Indeksin pistelukujen muutos"]
            [:div [:strong (fmt/desimaaliluku (:pistelukujen_muutos_prosentteina paatos) 1 false) "%"]]]

           [:div.flex-row.summa-rivi
            [:div (str "Indeksikorjauksen prosenttiosuus (2% " (if (> 0 (:pistelukujen_muutos_prosentteina paatos))
                                                                 "alittava"
                                                                 "ylittävä") " osa)")]
            [:div [:strong (fmt/desimaaliluku (:indeksikorotuksen_prosenttiosuus paatos) 1 false) "%"]]]

           [:div.flex-row.laskenta-linkki-matalampi
            [yleiset/linkki "Näytä laskenta"
             (fn [] (modal/nayta! {:otsikko "Laskenta"
                                   :otsikko-muotoilut {:font-size "32px"}
                                   :body-tyyli {:margin-bottom "16px"}
                                   :content-tyyli {:padding-top "24px" :padding-bottom "24px"}
                                   :footer [napit/sulje #(modal/piilota!)]
                                   :footer-tyyli {:text-align "left"}}
                      [laskenta-modaali paatos]))
             {:style {:text-decoration :underline}}]]

           [:div.flex-row {:aria-live "polite"}
            ;;Tämä :aria-live on tässä ruudunlukijaa varten, jotta se jätä tätä linkin jälkeen olevaa h3-otsikkoa lukematta (tapahtui ainakin Windowsin Lukija-toiminnolla)
            [:div.big-text "Hoitovuoden lopun indeksikorjaus"]
            [:div.big-text.lihavoitu (fmt/euro-opt false (:hoitokauden_lopun_indeksikorjaus paatos))]]
           [:hr.paatos-hr]

           [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? voi-muokata?
            #(e! (valikatselmus-tiedot/->TallennaHoitovuodenlopunIndeksikorjauspaatos paatos))
            #(e! (valikatselmus-tiedot/->PoistaHoitovuodenlopunIndeksikorjauspaatos paatos))]]
          [:div.muokkaustoiminnot
           [yleiset/info-laatikko :neutraali (:virhe paatos) nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])])]))
