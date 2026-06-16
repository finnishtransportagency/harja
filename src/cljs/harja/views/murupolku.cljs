(ns harja.views.murupolku
  "Murupolku on sovelluksenlaajuinen navigaatiokomponentti.
  Sen avulla voidaan vaikuttaa sovelluksen tilaan muun muassa
  seuraavia parametrejä käyttäen: väylämuoto, hallintayksikkö,
  urakka, urakan tyyppi, urakoitsija."
  (:require [reagent.core :refer [atom]]

            [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.domain.roolit :as roolit]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.kokoelmat :refer [distinct-by]]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.urakoitsijat :as urakoitsijat]))


(defn hallintayksikko [_valinta-auki]
  (let [vaihtoehdot @hal/vaylamuodon-hallintayksikot]
    [:div.murupolkuvalitsin
     [:div.d-flex.align-items-center.gap-2.mb-1
      [:label.form-label.mb-0 {:for "hallintayksikko-select"} "Elinvoimakeskus"]
      [:a.text-secondary
       {:href "#"
        :on-click (fn [e]
                    (.preventDefault e)
                    (nav/valitse-hallintayksikko-varmistuksella! nil))}
       "/ Koko maa"]]

     [yleiset/livi-pudotusvalikko
      {:valitse-fn (fn [{:keys [id] :as e}]
                     (let [valinta (some #(when
                                            (= (str (:id %)) (str id)) %) vaihtoehdot)]
                       (nav/valitse-hallintayksikko-varmistuksella! valinta)))
       :valinta @nav/valittu-hallintayksikko
       :class "livi-alasveto-250 alasveto-hallintayksikko"
       :format-fn #(if % (:nimi %) "- valitse -")}
      vaihtoehdot]]))


(defn urakka [_valinta-auki]
  (when @nav/valittu-hallintayksikko
    (let [vaihtoehdot (->> @nav/suodatettu-urakkalista
                        (filter #(pvm/jalkeen? (:loppupvm %) (pvm/nyt)))
                        (sort-by :nimi))
          hae-valinta (fn [arvo]
                        (when arvo
                          (some #(when (= (str (:id %)) (str arvo)) %) vaihtoehdot)))]

      ;; ===============================
      ;; Pidennä hieman jos urakka valittuna 
      [:div
       {:style (when (some-> @nav/valittu-urakka :id str) {:min-width "300px"})}
       [:label.form-label {:for "alasveto-urakka"} "Urakka"]

       [yleiset/livi-pudotusvalikko
        {:valitse-fn (fn [e]
                       (nav/valitse-urakka-varmistuksella! (hae-valinta (:id e))))
         :valinta @nav/valittu-urakka
         :class "livi-alasveto-250 alasveto-urakka"
         :data-cy "urakat-valitse-urakka"
         :format-fn #(if % (:nimi %) "- valitse -")}
        vaihtoehdot]])))


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
                      vec)]
    [:div.murupolku-urakoitsija
     [:label.form-label {:for "alasveto-urakoitsija"} "Urakoitsija"]

     [yleiset/livi-pudotusvalikko
      {:valitse-fn (fn [e]
                     (let [id (str (:id e))
                           valinta (some #(when (= (str (:id %)) id) %) vaihtoehdot)]
                       (nav/valitse-urakoitsija-varmistuksella! valinta)))
       :disabled disabled?
       :valinta valittu
       :class "livi-alasveto-250"
       :format-fn #(if % (:nimi %) "- valitse -")}
      vaihtoehdot]]))


(defn urakkatyyppi []
  (let [valittu @nav/urakkatyyppi
        disabled? (boolean @nav/valittu-urakka)
        vaihtoehdot nav/+urakkatyypit-ja-kaikki+]
    [:div.murupolku-urakkatyyppi
     [:label.form-label {:for "alasveto-urakkatyyppi"} "Urakkatyyppi"]

     [yleiset/livi-pudotusvalikko
      {:valitse-fn (fn [e]
                     (let [tyyppi (str (:arvo e))
                           valinta (some #(when (= (str (:arvo %)) tyyppi) %) vaihtoehdot)]
                       (nav/vaihda-urakkatyyppi! valinta)))
       :disabled disabled?
       :valinta valittu
       :data-cy "murupolku-urakkatyyppi"
       :class "livi-alasveto-250"
       :format-fn #(if % (:nimi %) "- valitse -")}
      vaihtoehdot]]))


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
                 :class (if @nav/murupolku-nakyvissa?
                          "murupolku d-flex flex-wrap justify-content-between align-items-start w-100"
                          "hide")}

           [:div {:class "d-flex flex-wrap align-items-end gap-3"
                  :style {:flex "1 1 420px"}}
            [hallintayksikko valinta-auki]
            [urakka valinta-auki]]

           (when ei-urakkaa?
             [:div {:class "d-flex flex-wrap align-items-end gap-3 justify-content-start justify-content-lg-end"
                    :style {:flex "1 1 420px"}}
              [urakkatyyppi]
              (when-not urakoitsija? [urakoitsija])])])))))
