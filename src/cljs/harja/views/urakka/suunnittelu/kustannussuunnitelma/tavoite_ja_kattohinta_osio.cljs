(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoite-ja-kattohinta-osio
  (:require [reagent.core :as r :refer [cursor]]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.grid :as v-grid]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]))


;; -- Tavoite- ja kattohinta osioon liittyvät gridit --

;; NOTE: Gridit määritellään kustannussuunnitelma_view pääkomponentissa suoraan maarataulukko-grid apurilla.

;; | -- Gridit päättyy



;; -----
;; -- Tavoite- ja kattohinta osion apufunktiot --

(defn- tavoitehinta-yhteenveto
  [tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [ks-yhteiset/hintalaskuri
      {:otsikko "Tavoitehinta"
       :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
       :hinnat (update tavoitehinnat 0 assoc :teksti "1. vuosi*")
       :data-cy "tavoitehinnan-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri tavoitehinnat indeksit {:dom-id "tavoitehinnan-indeksikorjaus"
                                                         :data-cy "tavoitehinnan-indeksilaskuri"}]]
    [yleiset/ajax-loader]))

(defn- manuaalinen-kattohinta-grid
  [e! tavoitehinnat kantahaku-valmis?]
   [:h5 "Kattohinta"]
   (if kantahaku-valmis?
     [:div
      {:on-blur #(e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))}
      [v-grid/muokkaus-grid
       {:otsikko "Kattohinta"
        :luokat ["kattohinta-grid"]
        :data-cy "manuaalinen-kattohinta-grid"
        :piilota-toiminnot? true
        :muokkauspaneeli? false
        :muutos #(e! (t/->PaivitaKattohintaGrid %))
        :valiotsikot {1 (v-grid/otsikko "Indeksikorjatut")}
        :disabloi-rivi? #(not= :kattohinta (:rivi %))
        :sisalto-kun-rivi-disabloitu #(fmt/euro-opt ((:nimi %) (:rivi %)))}
       (merge
         (mapv (fn [hoitovuosi-numero]
                 (let [tavoitehinta (:summa (nth tavoitehinnat (dec hoitovuosi-numero)))]
                   {:otsikko (str hoitovuosi-numero ".hoitovuosi")
                    :nimi (keyword (str "kattohinta-vuosi-" hoitovuosi-numero))
                    :fmt fmt/euro-opt
                    :validoi [[:manuaalinen-kattohinta tavoitehinta]]
                    :tyyppi :positiivinen-numero}))
           (range 1 6))
         {:otsikko "Yhteensä"
          :nimi :yhteensa
          :tyyppi :positiivinen-numero
          :muokattava? (constantly false)
          :disabled? true
          :fmt fmt/euro-opt
          :hae #(apply + (vals
                           (select-keys % (mapv
                                            (fn [hoitovuosi-nro]
                                              (keyword (str "kattohinta-vuosi-" hoitovuosi-nro)))
                                            (range 1 6)))))})
       tiedot/kustannussuunnitelma-kattohinta]]
     [yleiset/ajax-loader]))

(defn- kattohinta-yhteenveto
  [kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [ks-yhteiset/hintalaskuri
      {:otsikko "Kattohinta"
       :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
       :hinnat kattohinnat
       :data-cy "kattohinnan-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri kattohinnat indeksit {:dom-id "kattohinnan-indeksikorjaus"
                                                       :data-cy "kattohinnan-indeksilaskuri"}]]
    [yleiset/ajax-loader]))



;; ### Tavoite- ja kattohinta osion pääkomponentti ###
;; Käyttää vaihtoehtoista harja.ui.grid/muokkaus - komponenttia

(defn osio
  [e! urakka yhteenvedot kuluva-hoitokausi indeksit kantahaku-valmis?]
  ;; TODO: Toteuta kattohinnalle käsin syöttämisen mahdollisuus myöhemmin: VHAR-4858
  (let [tavoitehinnat (mapv (fn [summa]
                              {:summa summa})
                        (t/tavoitehinnan-summaus yhteenvedot))
        kattohinnat (mapv #(update % :summa * 1.1) tavoitehinnat)
        urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))
        manuaalinen-kattohinta? (some #(= urakan-aloitusvuosi %) t/manuaalisen-kattohinnan-syoton-vuodet)]
    [:<>
     [:h3 {:id (str "tavoite-ja-kattohinta" "-osio")} "Tavoite- ja kattohinta"]
     [tavoitehinta-yhteenveto tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
     [:span#tavoite-ja-kattohinta-huomio "Vuodet ovat hoitovuosia"]
     (if manuaalinen-kattohinta?
       [manuaalinen-kattohinta-grid e! tavoitehinnat kantahaku-valmis?]
       [kattohinta-yhteenveto kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?])]))