(ns harja.views.urakka.valikatselmus.hintapaatokset
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [reagent.core :as r :refer [atom]]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.validointi :as validointi]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn tavoitehinnan-ylitys [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :tavoitehinnan-ylitys
        paatos-tehty? (some? (:id paatos))
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "tavoitehinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-reunuksella

     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan ylitys" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]

     (when tallennus-kesken?
       [yleiset/ajax-loader-pieni "Tallennetaan tietoja..."])

     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div.big-text "Tavoitehinnan ylitys"]
         [:div.big-text.lihavoitu (fmt/euro-opt false (:ylityksen_maara paatos))]]
        [:div.flex-row.summa-rivi-valja
         [:div (str "Tilaaja maksaa (" (:tilaajan_prosentti paatos) "%)")]
         [:div.rivi-lukema (fmt/euro-opt false (:tilaaja_maksaa paatos))]]
        [:div.flex-row.summa-rivi-matala
         [:div (str "Urakoitsija maksaa (" (:urakoitsijan_prosentti paatos) "%)")]
         [:div.rivi-lukema (fmt/euro-opt false (:urakoitsija_maksaa paatos))]]

        [:hr.paatos-hr]

        [:div.muokkaustoiminnot
         (when (:virheet paatos)
           [yleiset/info-laatikko :vahva-ilmoitus "Et voi vahvistaa päätöstä, sillä osa pohjatiedoista puuttuu" (:virheet paatos) nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}])
         [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken?
          (and voi-muokata? (not (:virheet paatos)))
          ;; Vahvista
          #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanYlitysPaatos paatos))
          ;; Peru päätös
          #(e! (valikatselmus-tiedot/->HaeKetjutetustiKumoutuvatPaatokset
                 paatos
                 (fn [] (e! (valikatselmus-tiedot/->PeruValikatselmusPaatos paatos)))))]]])]))

(defn- tavoitehinnan-laskentamodaali [paatos]
  (let []
    [:div
     [:div.flex-row
      [:p.laskenta-rivi "Mikäli toteutuneiden hankintakustannusten, johto- ja hallintakorvauksen sekä hoidonjohtopalkkion yhteisarvo alittaa kyseisen hoitovuoden tavoitehinnan, maksetaan
      urakoitsijalle tavoitepalkkiota 30% hoitovuoden tavoitehinnan alituksesta."]]
     [:div.flex-row
      [:p.laskenta-rivi "Jos tavoitepalkkio > 3 %, siirretään ylittävä osuus seuraavan hoitovuoden hoitotöiden hankintakustannuksiin alennukseksi."]]
     [:div.flex-row
      [:p.laskenta-rivi "Poikkeus: viimeisenä hoitovuonna ei ole siirtomahdollisuutta. Tavoitepalkkio maksetaan täysimääräisesti koko viimeisen vuoden hoitovuoden
      lopun tavoitehinnan alittavasta osuudesta."]]]))

(defn tavoitehinnan-alitus [e! paatos tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :tavoitehinnan-alitus
        paatos-tehty? (some? (:id paatos))
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "tavoitehinnan-alitus-" (gensym))}
    [:div.paatos-komponentti-reunuksella
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan alitus" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div.big-text "Tavoitehinnan alitus"]
         [:div.big-text.lihavoitu (fmt/euro-opt false (:alituksen_maara paatos))]]
        [:div.flex-row.lista-rivi-ylin
         [:div (str "Tavoitepalkkio (" (:tavoitepalkkion_maksuprosentti paatos) "%)")]
         [:div.rivi-lukema (fmt/euro-opt false (:tavoitepalkkio paatos))]]
        [:div.flex-row
         [:div.small-text.lisays.harmaa (str "max. " (:tavoitepalkkion_maksimi_prosentti paatos) "% hoitovuoden alun indeksikorjatusta tavoitehinnasta.")]]
        ;; Näytetään siirron määrä vain, jos sitä on. Esim viimeisenä vuotena ei siirretä mitään.
        (when (and (:siirron_maara paatos) (not= 0 (:siirron_maara paatos)))
          [:div.flex-row.lista-rivi-korkea
           [:div "Siirretään seuraavan vuoden hankintakustannuksiin alennukseksi"]
           [:div.rivi-lukema (fmt/euro-opt false (:siirron_maara paatos))]])

        [:div.flex-row.laskenta-linkki-matalampi
         [yleiset/linkki "Näytä laskenta"
          (fn [] (modal/nayta! {:otsikko "Laskenta"
                                :otsikko-muotoilut {:font-size "32px"}
                                :body-tyyli {:margin-bottom "16px"}
                                :content-tyyli {:padding-top "24px" :padding-bottom "24px"}
                                :footer [napit/sulje #(modal/piilota!)]
                                :footer-tyyli {:text-align "left"}}
                   [tavoitehinnan-laskentamodaali paatos]))
          {:style {:text-decoration :underline}}]]

        [:hr.paatos-hr]

        ;; Päätöksenteko napit tai mahdollinen virhe
        [:div.muokkaustoiminnot
         (when (:virheet paatos)
           [yleiset/info-laatikko :vahva-ilmoitus "Et voi vahvistaa päätöstä, sillä osa pohjatiedoista puuttuu" (:virheet paatos) nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}])
         [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? (not (:virheet paatos))
          ;; Vahvista 
          #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanAlitusPaatos paatos))
          ;; Peru päätös 
          #(e! (valikatselmus-tiedot/->HaeKetjutetustiKumoutuvatPaatokset
                 paatos
                 (fn [] (e! (valikatselmus-tiedot/->PeruValikatselmusPaatos paatos)))))]]])]))

(defn kattohinnan-ylitys [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :kattohinnan-ylitys
        paatos-tehty? (some? (:id paatos))
        siirra? (:siirra? paatos)
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        siirrettava (atom (if (:siirrettava_maara paatos) (:siirrettava_maara paatos) 0))
        siirtorajoitus? (when (:siirtorajoitus_prosentti paatos) true)]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-reunuksella

     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Kattohinnan ylitys" paatos-tehty? paatos-avain avatut-paatokset
      (partial valikatselmus-tiedot/avaa-tai-sulje-haitari) (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]

     (when tallennus-kesken?
       [yleiset/ajax-loader-pieni "Tallennetaan tietoja..."])

     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div.big-text "Kattohinnan ylitys"]
         [:div.big-text.lihavoitu (fmt/euro-opt false (:ylityksen_maara paatos))]]
        (when (and (not paatos-tehty?) (not (:viimeinen_hoitokausi paatos)))
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
                                                                :on-blur #(e! (valikatselmus-tiedot/->PaivitaKattohinnanSiirtoMaara @siirrettava))
                                                                :disabled? (or paatos-tehty? false)}
                                                :arvo-atom siirrettava}])]]
           (when (and siirra? siirtorajoitus?)
             [:div.selite
              [:p "*Enintään 3% laskettuna hoitovuoden lopun kattohinnasta."]])])

        [:div.flex-row.summa-rivi-korkea
         [:div "Urakoitsija maksaa"]
         [:div.rivi-lukema (fmt/euro-opt false (:urakoitsija_maksaa paatos))]]
        (when (and (:siirrettava_maara paatos) (> (:siirrettava_maara paatos) 0))
          [:div.flex-row.summa-rivi-matala
           [:div "Siirretään seuraavalle hoitovuodelle"]
           [:div.rivi-lukema (fmt/euro-opt false (:siirrettava_maara paatos))]])
        [:hr.paatos-hr]

        (when (:virhe paatos)
          [:div.muokkaustoiminnot
           [yleiset/info-laatikko :varoitus (:virhe paatos) nil nil {:sulje-nappi-id (gensym)}]])

        ;; Päätöksenteko napit
        [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? voi-muokata?
         ;; Vahvista
         #(e! (valikatselmus-tiedot/->TallennaKattohinnanYlitysPaatos paatos))
         ;; Peru päätös 
         #(e! (valikatselmus-tiedot/->HaeKetjutetustiKumoutuvatPaatokset
                paatos
                (fn [] (e! (valikatselmus-tiedot/->PeruValikatselmusPaatos paatos)))))]])]))
