(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.alasvedot
  (:require [reagent.core :as r]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(defn- livi-pudotusvalikko [komponentti]
  (r/with-let [valinta (r/atom "Urakat")]
    [kehys/kortti komponentti
     [:p "Valinta välitetään :valitse-fn-funktiolle. Komponentti renderöi nykyisellään .dropdown- ja .dropdown-menu-rakenteen."]
     [yleiset/pudotusvalikko
      "Näkymä"
      {:data-cy (kehys/ankkuri komponentti "-valikko")
       :valinta @valinta
       :valitse-fn #(reset! valinta %)
       :format-fn str}
      ["Urakat" "Raportit" "Hallinta"]]
     [:p {:data-cy (kehys/ankkuri komponentti "-arvo")}
      (str "Valittu: " @valinta)]
     [napit/nappi "Palauta oletus" #(reset! valinta "Urakat")
      {:data-cy (kehys/ankkuri komponentti "-palauta")
       :luokka "nappi-toissijainen"}]]))

(defn- checkbox-pudotusvalikko [komponentti]
  (r/with-let [valinnat (r/atom [{:nimi "Urakka A" :valittu? true}
                                 {:nimi "Urakka B" :valittu? false}
                                 {:nimi "Urakka C" :valittu? false}])]
    [kehys/kortti komponentti
     [:p "Valinnat välitetään :valitse-fn-funktiolle valinnan ja valitun tilan kanssa. Komponentti renderöi .checkbox-pudotusvalikko-rakenteen."]
     [valinnat/checkbox-pudotusvalikko
      @valinnat
      (fn [valinta valittu?]
        (swap! valinnat (fn [vaihtoehdot]
                          (mapv #(if (= (:nimi %) (:nimi valinta))
                                   (assoc % :valittu? valittu?)
                                   %)
                                vaihtoehdot))))
      [" urakka" " urakkaa"]
      {:data-cy (kehys/ankkuri komponentti "-valikko")}]
     [:p {:data-cy (kehys/ankkuri komponentti "-arvo")}
      (str "Valitut: " (mapv :nimi (filter :valittu? @valinnat)))]
     [napit/nappi "Palauta oletus"
      #(reset! valinnat [{:nimi "Urakka A" :valittu? true}
                         {:nimi "Urakka B" :valittu? false}
                         {:nimi "Urakka C" :valittu? false}])
      {:data-cy (kehys/ankkuri komponentti "-palauta")
       :luokka "nappi-toissijainen"}]]))

(def ^:private esikatselun-hoitokaudet
  [[(pvm/hoitokauden-alkupvm 2024) (pvm/hoitokauden-loppupvm 2025)]
   [(pvm/hoitokauden-alkupvm 2025) (pvm/hoitokauden-loppupvm 2026)]
   [(pvm/hoitokauden-alkupvm 2026) (pvm/hoitokauden-loppupvm 2027)]])

(defn- hoitokausi-esikatselu [komponentti]
  (r/with-let [valittu (r/atom (second esikatselun-hoitokaudet))]
    [kehys/kortti komponentti
     [:p "Hoitokauden valinta välitetään valitse-fn-funktiolle. Komponentti ei lue urakkaa, vaan saa hoitokaudet parametrina."]
     [:div {:data-cy (kehys/ankkuri komponentti "-valikko")}
      [valinnat/hoitokausi {} esikatselun-hoitokaudet valittu #(reset! valittu %)]]
     [:p {:data-cy (kehys/ankkuri komponentti "-arvo")}
      (str "Valittu: " (when @valittu (fmt/pvm-vali-opt @valittu)))]
     [napit/nappi "Palauta oletus" #(reset! valittu (second esikatselun-hoitokaudet))
      {:data-cy (kehys/ankkuri komponentti "-palauta")
       :luokka "nappi-toissijainen"}]]))

(defn- simulaattorin-vaihtoehto-teksti [vaihtoehto]
  (if (keyword? vaihtoehto) (name vaihtoehto) (str vaihtoehto)))

(defn- livi-pudotusvalikko-simulaattorissa-sisalto [solmu]
  (r/with-let [valinta (r/atom (first (get-in solmu [:asetukset :valinnat])))]
    (let [{:keys [otsikko valinnat]} (:asetukset solmu)]
      [:div {:data-cy "ui-simulaattori-livi-pudotusvalikko"}
       [yleiset/pudotusvalikko
        (or otsikko "Valinta")
        {:valinta @valinta
         :valitse-fn #(reset! valinta %)
         :format-fn simulaattorin-vaihtoehto-teksti}
        (vec valinnat)]])))

;; Simulaattorin data ei saa sisältää funktioita tai atomeja, joten renderöijä antaa nämä itse
;; ja välittää asetuksista vain datana annettavat arvot (:otsikko, :valinnat).
(defn- livi-pudotusvalikko-simulaattorissa [solmu _tila _lapsi-fn]
  [livi-pudotusvalikko-simulaattorissa-sisalto solmu])

(defn- hoitokausi-simulaattorissa-sisalto []
  (r/with-let [valittu (r/atom (second esikatselun-hoitokaudet))]
    [:div {:data-cy "ui-simulaattori-hoitokausi"}
     [valinnat/hoitokausi {} esikatselun-hoitokaudet valittu #(reset! valittu %)]]))

(defn- hoitokausi-simulaattorissa [_solmu _tila _lapsi-fn]
  [hoitokausi-simulaattorissa-sisalto])

(def ^:private checkbox-pudotusvalikon-oletusteksti [" valinta" " valintaa"])

(defn- checkbox-pudotusvalikko-simulaattorissa-sisalto [solmu]
  (r/with-let [valinnat (r/atom (mapv #(select-keys % [:nimi :valittu?])
                                      (get-in solmu [:asetukset :valinnat])))]
    (let [teksti (or (get-in solmu [:asetukset :teksti]) checkbox-pudotusvalikon-oletusteksti)]
      [:div {:data-cy "ui-simulaattori-checkbox-pudotusvalikko"}
       [valinnat/checkbox-pudotusvalikko
        @valinnat
        (fn [valinta valittu?]
          (swap! valinnat (fn [vaihtoehdot]
                            (mapv #(if (= (:nimi %) (:nimi valinta))
                                     (assoc % :valittu? valittu?)
                                     %)
                                  vaihtoehdot))))
        teksti
        {}]])))

(defn- checkbox-pudotusvalikko-simulaattorissa [solmu _tila _lapsi-fn]
  [checkbox-pudotusvalikko-simulaattorissa-sisalto solmu])

(register! :livi-pudotusvalikko {:esikatselu livi-pudotusvalikko
                                 :renderoi livi-pudotusvalikko-simulaattorissa})
(register! :checkbox-pudotusvalikko {:esikatselu checkbox-pudotusvalikko
                                     :renderoi checkbox-pudotusvalikko-simulaattorissa})
(register! :hoitokausi {:esikatselu hoitokausi-esikatselu
                        :renderoi hoitokausi-simulaattorissa})
