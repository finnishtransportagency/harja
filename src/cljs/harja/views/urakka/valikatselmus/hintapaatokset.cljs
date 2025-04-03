(ns harja.views.urakka.valikatselmus.hintapaatokset
  (:require [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.validointi :as validointi]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn tavoitehinnan-ylitys [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
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
         [:div [:h3.matala "Tavoitehinnan ylitys"]]
         [:div.otsikko_lukema (fmt/euro-opt false (:ylityksen_maara paatos))]]
       [:div.flex-row.summa_rivi_ylin
        [:div (str "Tilaaja maksaa (" (:tilaajan_prosentti paatos) "%)")]
        [:div.rivi_lukema (fmt/euro-opt false (:tilaaja_maksaa paatos))]]
       [:div.flex-row.summa_rivi_alin
        [:div (str "Urakoitsija maksaa (" (:urakoitsijan_prosentti paatos) "%)")]
        [:div.rivi_lukema (fmt/euro-opt false (:urakoitsija_maksaa paatos))]]

       ;; Päätöksenteko napit
       (if (not paatos-tehty?)
         [:div.paatos-toiminto
          (when on-oikeudet?
            [napit/yleinen-ensisijainen "Tallenna päätös"
             #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanYlitysPaatos paatos))
             {:ikoni [ikonit/harja-icon-status-selected]
              :disabled (or
                          tallennus-kesken?
                          (not voi-muokata?))}])]
         [:div.paatos-toiminto
          (when on-oikeudet?
            [napit/nappi
             "Peru päätös"
             #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanYlitysPaatos paatos))
             {:luokka "nappi-toissijainen"
              :ikoni [ikonit/harja-icon-action-undo]
              :disabled (or
                          tallennus-kesken?
                          (not voi-muokata?))}])])])]))

(defn tavoitehinnan-alitus [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
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
         [:div [:h3.matala "Tavoitehinnan alitus"]]
         [:div.otsikko_lukema (fmt/euro-opt false (:alituksen_maara paatos))]]
        [:div.flex-row
         [:div (str "Tavoitepalkkio (" (:tavoitepalkkion_maksuprosentti paatos) "%)")]
         [:div.rivi_lukema (fmt/euro-opt false (:tavoitepalkkio paatos))]]
        [:div.flex-row
         [:div.small-text.lisays.harmaa (str "max. " (:tavoitepalkkion_maksimi_prosentti paatos) "% hoitovuoden alun indeksikorjatusta tavoitehinnasta.")]]
        ;; Näytetään siirron määrä vain, jos sitä on. Esim viimeisenä vuotena ei siirretä mitään.
        (when (and (:siirron_maara paatos)  (not= 0 (:siirron_maara paatos)))
          [:div.flex-row.summa_rivi_korkea
           [:div "Siirretään seuraavan vuoden hankintakustannuksiin alennukseksi"]
           [:div.rivi_lukema (str "-" (fmt/euro-opt false (:siirron_maara paatos)))]])

        ;; Päätöksenteko napit tai mahdollinen virhe
        (if (:virhe paatos)
          [:div.muokkaustoiminnot
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos) nil nil {:vari "@gray25"}]]

          (if (not paatos-tehty?)
            [:div.paatos-toiminto
             (when on-oikeudet?
               [napit/yleinen-ensisijainen "Tallenna päätös"
                #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanAlitusPaatos paatoksen-tiedot))
                {:ikoni [ikonit/harja-icon-status-selected]
                 :disabled (or
                             tallennus-kesken?
                             (not voi-muokata?))}])]
            [:div.paatos-toiminto
             (when on-oikeudet?
               [napit/nappi
                "Peru päätös"
                #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanAlitusPaatos paatoksen-tiedot))
                {:luokka "nappi-toissijainen"
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
         [:div [:h3.matala "Kattohinnan ylitys"]]
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
          [:div.flex-row.summa_rivi
           [:div "Siirrettävä määrä"]
           [:div.rivi_lukema (fmt/euro-opt false (:siirrettava_maara paatos))]])

        ;; Päätöksenteko napit - TODO: Tsekkaappa, että voisko nämä kaikki napit komponentisoida, kun tekevät kuitenkin kaikissa päätöksissä ihan sammaa asiaa.
        (if (not paatos-tehty?)
          [:div.paatos-toiminto
           (when on-oikeudet?
             [napit/yleinen-ensisijainen "Tallenna päätös"
              #(e! (valikatselmus-tiedot/->TallennaKattohinnanYlitysPaatos paatos))
              {:ikoni [ikonit/harja-icon-status-selected]
               :disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])]
          [:div.paatos-toiminto
           (when on-oikeudet?
             [napit/nappi
              "Peru päätös"
              #(e! (valikatselmus-tiedot/->PoistaKattohinnanYlitysPaatos paatos))
              {:luokka "nappi-toissijainen"
               :ikoni [ikonit/harja-icon-action-undo]
               :disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])])])]))
