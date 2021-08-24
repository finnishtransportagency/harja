(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoite-ja-kattohinta-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]))


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

(defn osio
  [yhteenvedot kuluva-hoitokausi indeksit kantahaku-valmis?]
  ;; TODO: Toteuta kattohinnalle käsin syöttämisen mahdollisuus myöhemmin: VHAR-4858
  (let [tavoitehinnat (mapv (fn [summa]
                              {:summa summa})
                        (t/tavoitehinnan-summaus yhteenvedot))
        kattohinnat (mapv #(update % :summa * 1.1) tavoitehinnat)]
    [:<>
     [:h3 {:id (str "tavoite-ja-kattohinta" "-osio")} "Tavoite- ja kattohinta"]
     [tavoitehinta-yhteenveto tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
     [:span#tavoite-ja-kattohinta-huomio "Vuodet ovat hoitovuosia"]
     [kattohinta-yhteenveto kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]]))