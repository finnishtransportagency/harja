(ns harja.views.urakka.valikatselmus.hintapaatokset
  (:require [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.urakka :as urakka]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.dom :as dom]
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
            [harja.validointi :as validointi]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn tavoitehinnan-ylitys [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :tavoitehinta-ylitys
        paatos-tehty? (or (:id paatos) false)
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "tavoitehinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan ylitys" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div [:h4 "Tavoitehinnan ylitys"]]
         [:div.bold (fmt/euro-opt (:ylityksen_maara paatos))]]
       [:div.flex-row
        [:div (str "Tilaaja maksaa (" (:tilaajan_prosentti paatos) "%)")]
        [:div (fmt/euro-opt (:tilaaja_maksaa paatos))]]
       [:div.flex-row
        [:div (str "Urakoitsija maksaa (" (:urakoitsijan_prosentti paatos) "%)")]
        [:div (fmt/euro-opt (:urakoitsija_maksaa paatos))]]

       ;; Päätöksenteko napit
       (if (not paatos-tehty?)
         [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
          (when on-oikeudet?
            [napit/yleinen-ensisijainen "Tallenna päätös"
             #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanYlitysPaatos paatos))
             {:disabled (or
                          tallennus-kesken?
                          (not voi-muokata?))}])]
         [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
          (when on-oikeudet?
            [napit/nappi
             "Kumoa päätös"
             #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanYlitysPaatos paatos))
             {:luokka "nappi-toissijainen napiton-nappi"
              :ikoni [ikonit/harja-icon-action-undo]
              :disabled (or
                          tallennus-kesken?
                          (not voi-muokata?))}])])])]))

(defn tavoitehinnan-alitus [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :tavoitehinta-alitus
        paatos-tehty? (boolean (:id paatos))
        paatoksen-tiedot (merge paatos
                           {:urakkaid (-> @tila/yleiset :urakka :id)})
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "tavoitehinnan-alitus-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan alitus" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div [:h4 "Tavoitehinnan alitus"]]
         [:div.bold (fmt/euro-opt (:alituksen_maara paatos))]]
        [:div.flex-row
         [:div (str "Tavoitepalkkio (" (:tavoitepalkkion_maksuprosentti paatos) "%)")]
         [:div (fmt/euro-opt (:tavoitepalkkio paatos))]]
        [:div.flex-row {:style {:margin-top "-5px"}}
         [:div.small-text.harmaa "max. 3% hoitovuoden alun indeksikorjatusta tavoitehinnasta."]]
        ;; Näytetään siirron määrä vain, jos sitä on. Esim viimeisenä vuotena ei siirretä mitään.
        (when (:siirron_maara paatos)
          [:div.flex-row
           [:div "Siirretään seuraavan vuoden hankintakustannuksiin alennukseksi"]
           [:div (str "-" (fmt/euro-opt (:siirron_maara paatos)))]])

        ;; Päätöksenteko napit tai mahdollinen virhe
        (if (:virhe paatos)
          [:div {:style {:padding-bottom "1rem"}}
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos)]]

          (if (not paatos-tehty?)
            [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
             (when on-oikeudet?
               [napit/yleinen-ensisijainen "Tallenna päätös"
                #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanAlitusPaatos paatoksen-tiedot))
                {:disabled (or
                             tallennus-kesken?
                             (not voi-muokata?))}])]
            [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
             (when on-oikeudet?
               [napit/nappi
                "Kumoa päätös"
                #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanAlitusPaatos paatoksen-tiedot))
                {:luokka "nappi-toissijainen napiton-nappi"
                 :ikoni [ikonit/harja-icon-action-undo]
                 :disabled (or
                             tallennus-kesken?
                             (not voi-muokata?))}])]))])]))

(defn kattohinnan-ylitys [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :kattohinta-ylitys
        paatos-tehty? (or (:id paatos) false)
        siirra? (:siirra? paatos)
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))
        siirrettava (atom (if (:siirrettava_maara paatos) (:siirrettava_maara paatos) 0))
        siirtorajoitus? (when (:siirtorajoitus_prosentti paatos) true)]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Kattohinnan ylitys" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div [:h3 "Kattohinnan ylitys"]]
         [:div.otsikko_lukema (fmt/euro-opt false (:ylityksen_maara paatos))]]
        (when (not (:viimeinen_hoitokausi paatos))
          [:div.harmaa-tausta
           [:div.flex-row
            [:div
             (when (and siirra? (not siirtorajoitus?)) {:class "checkbox-block"})
             [kentat/tee-kentta
              {:tyyppi :checkbox
               :disabled? (or paatos-tehty? false)
               :teksti "Aseta siirrettävä määrä (€)"
               :vayla-tyyli? true
               :nayta-rivina? true
               :iso-clickalue? true}
              (r/wrap
                siirra?
                (fn [uusi-arvo]
                  (e! (valikatselmus-tiedot/->PaivitaKattohinnanSiirtoCheckbox uusi-arvo))))]]
            [:div.siirrettavan-maaran-input (when (and siirra? siirtorajoitus?) {:style {:margin-bottom "8px"}})
             (when siirra?
               [kentat/tee-otsikollinen-kentta {:otsikko "Siirrettävä määrä (€)"
                                                :otsikon-luokka "caption"
                                                :luokka ""
                                                :alaotsikko (when siirtorajoitus? (str "Max. " (fmt/euro-opt false (:maksimi_siirrettava_maara paatos)) "*"))
                                                :alaotsikon-luokka "caption sub-caption"
                                                :kentta-params {:tyyppi :euro
                                                                :teksti-oikealla ""
                                                                :desimaalien-maara 2
                                                                :piilota-yksikko-otsikossa? true
                                                                :nimi :siirto
                                                                :veda-oikealle? true
                                                                :pakollinen? true
                                                                :vayla-tyyli? true
                                                                :elementin-id "kattohinta-ylitys-siirto"
                                                                :vaadi-ei-negatiivinen? true
                                                                :validoi-kentta-fn (fn [numero] (validointi/validoi-numero numero 0
                                                                                                  (if siirtorajoitus?
                                                                                                    (js/parseFloat (str/replace (fmt/desimaaliluku (:maksimi_siirrettava_maara paatos) 2 2 false) "," "."))
                                                                                                    (:ylityksen_maara paatos))
                                                                                                  2))
                                                                :on-blur #(e! (valikatselmus-tiedot/->PaivitaKattohinnanSiirtoMaara @siirrettava))
                                                                :disabled? (or paatos-tehty? false)}
                                                :arvo-atom siirrettava}])]]
           (when (and siirra? siirtorajoitus?)
             [:div.selite
              [:p (str "*Enintään 3% laskettuna hoitovuoden lopun kattohinnasta.")]])])

        [:div.flex-row.summa_rivi
         [:div "Urakoitsija maksaa"]
         [:div.rivi_lukema (fmt/euro-opt false (:urakoitsija_maksaa paatos))]]
        (when (and (:siirrettava_maara paatos) (> (:siirrettava_maara paatos) 0))
          [:div.flex-row.summa_rivi_alin
           [:div "Siirrettävä määrä"]
           [:div.rivi_lukema (fmt/euro-opt false (:siirrettava_maara paatos))]])

        ;; Päätöksenteko napit - TODO: Tsekkaappa, että voisko nämä kaikki napit komponentisoida, kun tekevät kuitenkin kaikissa päätöksissä ihan sammaa asiaa.
        (if (not paatos-tehty?)
          [:div.paatos-napit
           (when on-oikeudet?
             [napit/yleinen-ensisijainen "Tallenna päätös"
              #(e! (valikatselmus-tiedot/->TallennaKattohinnanYlitysPaatos paatos))
              {:ikoni [ikonit/harja-icon-status-selected]
               :disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])]
          [:div.paatos-napit
           (when on-oikeudet?
             [napit/nappi
              "Peru päätös"
              #(e! (valikatselmus-tiedot/->PoistaKattohinnanYlitysPaatos paatos))
              {:luokka "nappi-toissijainen napiton-nappi"
               :ikoni [ikonit/harja-icon-action-undo]
               :disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])])])]))
