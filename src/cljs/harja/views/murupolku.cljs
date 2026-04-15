(ns harja.views.murupolku
  "Murupolku on sovelluksenlaajuinen navigaatiokomponentti.
  Sen avulla voidaan vaikuttaa sovelluksen tilaan muun muassa
  seuraavia parametrejä käyttäen: väylämuoto, hallintayksikkö,
  urakka, urakan tyyppi, urakoitsija."
  (:require [reagent.core :refer [atom] :as r]

            [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
            [harja.ui.tom :refer [tom-select]]
            [harja.kokoelmat :refer [distinct-by]]

            [harja.ui.komponentti :as komp]
            [harja.domain.roolit :as roolit]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakoitsijat :as urakoitsijat]))


(defn hallintayksikko [_valinta-auki]
  (let [valittu @nav/valittu-hallintayksikko
        vaihtoehdot @hal/vaylamuodon-hallintayksikot]
    [:div.murupolku-select
     [:div.d-flex.align-items-center.gap-2.mb-1
      [:label.form-label.mb-0 {:for "hallintayksikko-select"} "Elinvoimakeskus"]
      ;; ===============================
      ;; Anna koko-maa valinta erikseen kuten on ollutkin 
      [:a.text-secondary
       {:href "#"
        :on-click (fn [e]
                    (.preventDefault e)
                    (nav/valitse-hallintayksikko-varmistuksella! nil))}
       "/ Koko maa"]]

     [tom-select
      {:id "hallintayksikko-select"
       :class "form-select"
       :value (or (some-> valittu :id str) "")
       :placeholder "Koko maa"
       :on-change (fn [e]
                    (let [id (.. e -target -value)
                          yksikko (some #(when (= (str (:id %)) id) %) vaihtoehdot)]
                      (nav/valitse-hallintayksikko-varmistuksella! yksikko)))
       :ts/options #js {:dropdownParent "body"
                        :allowEmptyOption true
                        :controlInput nil
                        :searchField #js []
                        :maxItems 1}}
      [:option {:value ""} "Koko maa"]

      (for [yksikko vaihtoehdot]
        ^{:key (:id yksikko)}
        [:option {:value (str (:id yksikko))}
         (hal/evknumero-ja-nimi yksikko)])]]))


(defn urakka [_valinta-auki]
  (when @nav/valittu-hallintayksikko
    (let [valittu @nav/valittu-urakka
          vaihtoehdot (->> @nav/suodatettu-urakkalista
                        (filter #(pvm/jalkeen? (:loppupvm %) (pvm/nyt)))
                        (sort-by :nimi))
          hae-valinta (fn [arvo]
                        (if (= arvo "")
                          nil
                          (some #(when (= (str (:id %)) arvo) %) vaihtoehdot)))]

      ;; ===============================
      ;; Pidennä hieman jos urakka valittuna 
      [:div.murupolku-select {:style (when (some-> valittu :id str) {:min-width "300px"})}
       [:label.form-label {:for "alasveto-urakka"} "Urakka"]
       [tom-select
        {:id "alasveto-urakka"
         :class "form-select w-100 select--nowrap"
         :value (or (some-> valittu :id str) "")
         :placeholder "- Urakka -"
         :on-change (fn [e]
                      (let [arvo (.. e -target -value)]
                        (nav/valitse-urakka-varmistuksella! (hae-valinta arvo))))
         :ts/options #js {:dropdownParent "body"
                          :allowEmptyOption true
                          :controlInput nil
                          :searchField #js []
                          :maxItems 1
                          :placeholder "- Urakka -"}}
        [:option {:value ""} "- Urakka -"]
        (for [urakka vaihtoehdot]
          ^{:key (:id urakka)}
          [:option {:value (str (:id urakka))}
           (:nimi urakka)])]])))


(defn urakoitsija []
  (let [valittu @nav/valittu-urakoitsija
        disabled? (or (some? @nav/valittu-urakka)
                    (= (:sivu @reitit/url-navigaatio) :raportit))
        vaihtoehdot (->> (case (:arvo @nav/urakkatyyppi)
                           :kaikki @urakoitsijat/urakoitsijat-kaikki
                           :hoito @urakoitsijat/urakoitsijat-hoito
                           :paallystys @urakoitsijat/urakoitsijat-paallystys
                           :tiemerkinta @urakoitsijat/urakoitsijat-tiemerkinta
                           :valaistus @urakoitsijat/urakoitsijat-valaistus
                           :vesivayla @urakoitsijat/urakoitsijat-vesivaylat
                           @urakoitsijat/urakoitsijat-hoito)
                      (remove nil?)
                      (distinct-by :id)
                      vec)
        hae-valinta (fn [arvo]
                      (if (= arvo "")
                        nil
                        (some #(when (= (str (:id %)) arvo) %) vaihtoehdot)))]
    [:div.murupolku-select
     [:label.form-label {:for "alasveto-urakoitsija"} "Urakoitsija"]

     [tom-select
      {:id "alasveto-urakoitsija"
       :class "form-select w-100"
       :value (or (some-> valittu :id str) "")
       :disabled disabled?
       :data-cy "murupolku-urakoitsija"
       :on-change (fn [e]
                    (let [arvo (.. e -target -value)]
                      (nav/valitse-urakoitsija-varmistuksella! (hae-valinta arvo))))
       :ts/options #js {:dropdownParent "body"
                        :allowEmptyOption true
                        :controlInput nil
                        :searchField #js []
                        :maxItems 1}}
      [:option {:value ""} "Kaikki"]
      (for [urakoitsija vaihtoehdot]
        ^{:key (:id urakoitsija)}
        [:option {:value (str (:id urakoitsija))}
         (:nimi urakoitsija)])]]))


(defn urakkatyyppi []
  (let [valittu @nav/urakkatyyppi
        disabled? (boolean @nav/valittu-urakka)
        vaihtoehdot nav/+urakkatyypit-ja-kaikki+
        ->arvo (fn [x]
                 (cond
                   (nil? x) ""
                   (:id x) (str (:id x))
                   (:nimi x) (:nimi x)
                   :else (str x)))
        hae-valinta (fn [arvo]
                      (some #(when (= (->arvo %) arvo) %) vaihtoehdot))]
    [:div.murupolku-select
     [:label.form-label {:for "alasveto-urakkatyyppi"} "Urakkatyyppi"]
     [tom-select
      {:id "alasveto-urakkatyyppi"
       :class (str "form-select" (when disabled? " disabled"))
       :value (->arvo valittu)
       :disabled disabled?
       :data-cy "murupolku-urakkatyyppi"
       :on-change (fn [e]
                    (let [arvo (.. e -target -value)]
                      (nav/vaihda-urakkatyyppi! (hae-valinta arvo))))
       :ts/options #js {:dropdownParent "body"
                        :allowEmptyOption true
                        :controlInput nil
                        :searchField #js []
                        :maxItems 1}}
      (for [tyyppi vaihtoehdot]
        ^{:key (->arvo tyyppi)}
        [:option {:value (->arvo tyyppi)}
         (if tyyppi (:nimi tyyppi) "Kaikki")])]]))


(defn murupolku []
  (let [valinta-auki (atom nil)]
    (komp/luo
      (komp/kuuntelija
        [:hallintayksikko-valittu :hallintayksikkovalinta-poistettu
         :urakka-valittu :urakkavalinta-poistettu]
        #(reset! valinta-auki false)
        :body-klikkaus (fn [this {klikkaus :tapahtuma}]
                         (when-not (dom/sisalla? this klikkaus)
                           (reset! valinta-auki false))))

      {:component-did-update (fn [_]
                               (t/julkaise! {:aihe :murupolku-naytetty-domissa?
                                             :naytetty? @nav/murupolku-nakyvissa?}))}
      (fn []
        (let [ur @nav/valittu-urakka
              ei-urakkaa? (nil? ur)
              urakoitsija? (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)]
          [:nav {:aria-label "murupolku"
                 :class (str "murupolku d-flex flex-wrap justify-content-between align-items-start w-100 "
                          (when (empty? @nav/tarvitsen-isoa-karttaa)
                            (if @nav/murupolku-nakyvissa? "" "hide")))}

           [:div {:class "d-flex flex-wrap align-items-end gap-3"
                  :style {:flex "1 1 420px"}}
            [hallintayksikko valinta-auki]
            [urakka valinta-auki]]

           (when ei-urakkaa?
             [:div {:class "d-flex flex-wrap align-items-end gap-3 justify-content-start justify-content-lg-end"
                    :style {:flex "1 1 420px"}}
              [urakkatyyppi]
              (when-not urakoitsija? [urakoitsija])])])))))
