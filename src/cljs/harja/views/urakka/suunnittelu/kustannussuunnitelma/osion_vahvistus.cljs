(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.osion-vahvistus
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.ui.modal :as modal]
            [harja.ui.debug :as debug]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :as napit]))


(defn selite-modal
  [laheta-fn! muuta-fn! vahvistus]
  [modal/modal {:otsikko "Sun pitää vahvistaa tää"
                :nakyvissa? true
                :sulje-fn #(e! (t/->SuljeVahvistus))}
   [:div "Please confirm"
    [:div "vahvistus" [debug/debug vahvistus]]
    (for [v (keys (:vahvistettavat-vuodet vahvistus))]
      [:div
       [:h3 (str "vuosi " v)]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :muutoksen-syy)}]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :selite)}]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :maara)}]])
    [:button {:on-click (r/partial laheta-fn! e! (:tiedot vahvistus))} "Klikkeris"]]])


(defn vahvista-suunnitelman-osa-komponentti
  "Komponentilla vahvistetaan yksittäinen kustannussuunnitelman osio.
  TODO: Keskeneräinen placeholder."
  [_ _]
  (let [auki? (r/atom false)
        tilaa-muutettu? false
        vahvista-suunnitelman-osa-fn #(e! (t/->VahvistaSuunnitelmanOsioVuodella {:tyyppi %1
                                                                                 :hoitovuosi %2}))
        avaa-tai-sulje #(swap! auki? not)]
    (fn [osion-nimi {:keys [hoitovuosi indeksit-saatavilla? on-tila?]}]
      (let [disabloitu? (not (and (roolit/tilaajan-kayttaja? @istunto/kayttaja)
                               indeksit-saatavilla?))]
        [:div.vahvista-suunnitelman-osa
         [:div.flex-row
          [yleiset/placeholder "IKONI"]
          (str "Vahvista suunnitelma ja hoitovuoden " hoitovuosi " indeksikorjaukset")
          [yleiset/placeholder (str "Auki? " @auki?)
           {:placeholderin-optiot {:on-click avaa-tai-sulje}}]]
         (when @auki?
           [:<>
            [:div.flex-row
             [yleiset/placeholder (pr-str @istunto/kayttaja)]
             [yleiset/placeholder (str "Oon auki" osion-nimi " ja disabloitu? " disabloitu? "ja on tila? " on-tila? " ja indeksit-saatavilla? " indeksit-saatavilla? " ja " (roolit/tilaajan-kayttaja? @istunto/kayttaja))]]
            [:div.flex-row
             "Jos suunnitelmaa muutetaan tämän jälkeen, ei erotukselle tehdä enää indeksikorjausta. Indeksikorjaus on laskettu vain alkuperäiseen lukuun."]
            [:div.flex-row
             (if (and on-tila?
                   (not disabloitu?)
                   (not tilaa-muutettu?))
               "Kumoa vahvistus"
               [napit/yleinen-ensisijainen "Vahvista"
                vahvista-suunnitelman-osa-fn
                {:disabled disabloitu?
                 :toiminto-args [osion-nimi hoitovuosi]}])
             [yleiset/placeholder (str (when disabloitu? "Vain urakan aluevastaava voi vahvistaa suunnitelman") indeksit-saatavilla? disabloitu?)]]])]))))
