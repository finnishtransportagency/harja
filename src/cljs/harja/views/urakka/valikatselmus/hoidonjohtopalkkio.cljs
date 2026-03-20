(ns harja.views.urakka.valikatselmus.hoidonjohtopalkkio
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn- laskenta-modaali [paatos]
  [:div
   [:div.flex-row
    [:p.yla-selite-korkea "Hoidonjohtopalkkioon tehdään muutos, jos hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia muuttuu"
     [:span.laskenta-rivi-lukema " > 5% "] "tarjouksen mukaisen tavoitehintaan verrattuna."]]

   [:div.flex-row.laskenta-rivi-matalampi
    [:div "Hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia"]
    [:div.laskenta-rivi-lukema (fmt/euro-opt (:hv_lopun_indkorjaamaton_tavoitehinta paatos))]]

   [:div.flex-row.laskenta-rivi-matalampi
    [:div "Tarjouksen mukainen tavoitehinta"]
    [:div.laskenta-rivi-lukema (fmt/euro-opt (:tarjouksen_tavoitehinta paatos))]]

   [:div.flex-row.laskenta-rivi-matalampi
    [:div (str "Muutosprosentti (" (fmt/euro-opt (:hv_lopun_indkorjaamaton_tavoitehinta paatos)) " / "
            (fmt/euro-opt (:tarjouksen_tavoitehinta paatos)) " - 1) * 100")]
    [:div.laskenta-rivi-lukema (fmt/desimaaliluku (:muutosprosentti paatos) 1) "%"]]

   (when-not (= 0 (:hoidonjohtopalkkio_muutos paatos))
    [:div.flex-row.laskenta-rivi-matalampi
     [:div "Hoitovuoden indeksikorjattu hoidonjohtopalkkio"]
     [:div.laskenta-rivi-lukema (fmt/euro-opt (:hoidonjohtopalkkio paatos))]])

   (when-not (= 0 (:hoidonjohtopalkkio_muutos paatos))
     [:div.row.laskenta-kaava
      [:div.laskenta-rivi-lukema.laskenta-avattuna "Hoidonjohtopalkkion muutos ="]
      [:div.laskenta-rivi-matalampi (str "(" (fmt/euro-opt (:hv_lopun_indkorjaamaton_tavoitehinta paatos)) " / "
                                      (fmt/euro-opt (:tarjouksen_tavoitehinta paatos)) " - 1) * "
                                      (fmt/euro-opt (:hoidonjohtopalkkio paatos))
                                      " = ")
       [:span.laskenta-rivi-matalampi.laskenta-rivi-lukema (fmt/euro-opt (:hoidonjohtopalkkio_muutos paatos))]]])])

(defn paatos [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :hoidonjohtopalkkion-muutos
        paatos-tehty? (some? (:id paatos))

        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-reunuksella
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Hoidonjohtopalkkion muutos" paatos-tehty? paatos-avain avatut-paatokset
      (partial valikatselmus-tiedot/avaa-tai-sulje-haitari) (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:p.yla-selite "Hoidonjohtopalkkioon tehdään muutos, jos hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia muuttuu"
         [:span.laskenta-rivi-lukema " > 5% "] "tarjouksen mukaisen tavoitehintaan verrattuna."]

        (if-not (:virhe paatos)
          [:div
           [:div.flex-row.prosentti-rivi
            [:div "Muutosprosentti"]
            [:div.rivi-lukema (fmt/desimaaliluku (:muutosprosentti paatos) 1) "%"]]

           [:div.flex-row
            [:div.big-text "Hoidonjohtopalkkion muutos"]
            [:div.big-text.lihavoitu (fmt/euro-opt false (:hoidonjohtopalkkio_muutos paatos))]]

           [:div.flex-row.laskenta-linkki
            [yleiset/linkki "Näytä laskenta"
             (fn [] (modal/nayta! {:otsikko "Laskenta"
                                   :otsikko-muotoilut {:font-size "32px"}
                                   :body-tyyli {:margin-bottom "24px"}
                                   :content-tyyli {:padding-top "24px" :padding-bottom "24px"}
                                   :footer [napit/sulje #(modal/piilota!)]
                                   :footer-tyyli {:text-align "left"}}
                      [laskenta-modaali paatos]))
             {:style {:text-decoration :underline}}]]

           [:hr.paatos-hr-korkeampi]

           [:div [yleiset/info-laatikko :neutraali (str
                                                     (if (= 0 (:hoidonjohtopalkkio_muutos paatos))
                                                       "Tavoitehinnan muutos on pienempi kuin 5%. Ei muuteta hoidonjohtopalkkiota."
                                                       "Päätöksen tallentaminen luo kulun Harjaan. Kulua ei lasketa tavoitehintaan.")) nil nil]]

           [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? voi-muokata?
            #(e! (valikatselmus-tiedot/->TallennaHoidonjohtopalkkionMuutospaatos paatos))
            (valikatselmus-yhteiset/paatoksen-poistovarmistus-modaali {:peru-paatos-fn #(e! (valikatselmus-tiedot/->PoistaHoidonjohtopalkkionMuutospaatos paatos))
                                                                       :teksti "Automaattisesti kirjattu kulu poistetaan."})]]
          [:div.muokkaustoiminnot
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos) nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])])]))
