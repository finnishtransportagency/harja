(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoite-ja-kattohinta-osio
  (:require [reagent.core :as r :refer [cursor]]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.grid :as v-grid]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.fmt :as fmt]))


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

;; TODO: Tarkista, minkälainen taulukko näytetään 2021-> alkaneille urakoille
(defn- kattohinta-sisalto
  [e! kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (or true kantahaku-valmis?)
    [v-grid/muokkaus-grid
     {:otsikko "Kattohinta"
      :luokat ["kattohinta-grid"]
      :piilota-toiminnot? true
      :muokkauspaneeli? false
      :muutos #(e! (t/->PaivitaKattohintaGrid %))
      :valiotsikot {1 (v-grid/otsikko "Indeksikorjatut")}
      :disabloi-rivi? #(not= :kattohinta (:rivi %))
      :sisalto-kun-rivi-disabloitu #(fmt/euro-opt ((:nimi %) (:rivi %)))}
     (merge
       (mapv (fn [hoitovuosi-numero]
               {:otsikko (str hoitovuosi-numero ".hoitovuosi")
                :nimi (keyword (str "kattohinta-vuosi-" hoitovuosi-numero))
                ;; TODO: Saako tuhateroitinta jotenkin?
                :fmt fmt/euro-opt
                :tyyppi :positiivinen-numero})
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
     tiedot/kustannussuunnitelma-kattohinta]
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
  [e! yhteenvedot kuluva-hoitokausi indeksit kantahaku-valmis?]
  ;; TODO: Toteuta kattohinnalle käsin syöttämisen mahdollisuus myöhemmin: VHAR-4858
  (let [tavoitehinnat (mapv (fn [summa]
                              {:summa summa})
                        (t/tavoitehinnan-summaus yhteenvedot))
        kattohinnat (mapv #(update % :summa * 1.1) tavoitehinnat)]
    [:<>
     [:h3 {:id (str "tavoite-ja-kattohinta" "-osio")} "Tavoite- ja kattohinta"]
     [tavoitehinta-yhteenveto tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
     [:span#tavoite-ja-kattohinta-huomio "Vuodet ovat hoitovuosia"]
     [kattohinta-sisalto e! kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
     [kattohinta-yhteenveto kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]]))