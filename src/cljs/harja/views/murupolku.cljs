(ns harja.views.murupolku
  "Murupolku on sovelluksenlaajuinen navigaatiokomponentti.
  Sen avulla voidaan vaikuttaa sovelluksen tilaan muun muassa
  seuraavia parametrejä käyttäen: väylämuoto, hallintayksikkö,
  urakka, urakan tyyppi, urakoitsija."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki alasveto-ei-loydoksia livi-pudotusvalikko]]

            [harja.loki :refer [log]]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakoitsijat :as urakoitsijat]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]))

(defn koko-maa []
  [:li
   [:a.murupolkuteksti {:href "#"
                        :style (when (nil? @nav/valittu-hallintayksikko)
                                 {:text-decoration "none"
                                  :color "#323232"})
                        :on-click #(do
                                    (.preventDefault %)
                                    (nav/valitse-hallintayksikko! nil))}
    "Koko maa"]])

(defn kasittele-toggle-napin-toiminnot [event {:keys [id valinta-auki valikon-tieto]}]
  (let [alasvetovalikon-rivit (vec (array-seq (.querySelectorAll (.getElementById js/document id) "li")))]
    (cond
      (or (dom/tab-nappain-ilman-shiftia? event) (dom/tab+shift-nappaimet? event))
      (do
        (when @valinta-auki
          (.preventDefault event)
          (.stopPropagation event)
          (reset! valinta-auki nil)))

      (or (dom/enter-nappain? event) (dom/valilyonti? event))
      (do
        (.preventDefault event)
        (.stopPropagation event)
        (if @valinta-auki
          (reset! valinta-auki nil)
          (do
            (reset! valinta-auki valikon-tieto)
            (when (get alasvetovalikon-rivit 0)
              (js/setTimeout #(.focus (get alasvetovalikon-rivit 0)) 150)))))

      (dom/esc-nappain? event)
      (reset! valinta-auki nil))))

(defn kasittele-alasvetovalikon-toiminnot [event {:keys [id valittu-rivi valinta-auki]}]
  (let [alasvetovalikon-rivit (vec (array-seq (.querySelectorAll (.getElementById js/document id) "li")))
        alasvedon-nappi (.querySelector (.getElementById js/document id) "button")]
    (cond
      (or (dom/tab-nappain-ilman-shiftia? event) (dom/tab+shift-nappaimet? event))
      (do
        (when @valinta-auki
          (.preventDefault event)
          (.stopPropagation event)
          (reset! valinta-auki nil)
          (reset! valittu-rivi 0)
          (r/after-render (fn [] (.focus alasvedon-nappi)))))

      (dom/nuoli-alas? event)
      (do
        (.preventDefault event)
        (.stopPropagation event)
        (reset! valittu-rivi (inc @valittu-rivi))
        (if (< @valittu-rivi (count alasvetovalikon-rivit))
          (.focus (get alasvetovalikon-rivit @valittu-rivi))
          (do (reset! valittu-rivi 0)
            (.focus (get alasvetovalikon-rivit @valittu-rivi)))))

      (dom/nuoli-ylos? event)
      (do
        (.preventDefault event)
        (.stopPropagation event)
        (reset! valittu-rivi (dec @valittu-rivi))
        (if (> @valittu-rivi -1)
          (.focus (get alasvetovalikon-rivit @valittu-rivi))
          (do (reset! valittu-rivi (dec (count alasvetovalikon-rivit)))
            (.focus (get alasvetovalikon-rivit @valittu-rivi)))))

      (dom/esc-nappain? event)
      (do
        (reset! valinta-auki nil)
        (reset! valittu-rivi 0)
        (r/after-render (fn [] (.focus alasvedon-nappi)))))))

(defn hallintayksikko [valinta-auki]
  (let [valittu @nav/valittu-hallintayksikko
        valittu-rivi (atom nil)]
    [:li.murupolkuvalitsin
     [:label {:for "alasveto-hallintayksikko"} "Hallintayksikkö"]
     [:div.dropdown.livi-alasveto {:id "alasveto-hallintayksikko"
                                  :class (when (= :hallintayksikko @valinta-auki) "open")}
      (let [vu @nav/valittu-urakka
            va @valinta-auki]
        (if (or (not (nil? vu)) (= va :hallintayksikko))
          [:a.murupolkuteksti {:href "#"
                               :on-click #(do
                                            (.preventDefault %)
                                            (nav/valitse-hallintayksikko! valittu))}
           (str (or (:nimi valittu) "- Hallintayksikkö -") " ")]

          [:span.valittu-hallintayksikko.murupolkuteksti (or (:nimi valittu) "- Hallintayksikkö -") " "]))

      [:button.nappi-murupolkualasveto.dropdown-toggle
       {:aria-label "Avaa hallintayksikkövalikko"
        :on-click #(swap! valinta-auki
                     (fn [v]
                       (if (= v :hallintayksikko)
                         nil
                         :hallintayksikko)))
        :on-key-down #(kasittele-toggle-napin-toiminnot % {:id "alasveto-hallintayksikko"
                                                           :valinta-auki valinta-auki
                                                           :valikon-tieto :hallintayksikko})}
       (if (= :hallintayksikko @valinta-auki)
         [:span.livicon-chevron-up]
         [:span.livicon-chevron-down])]

      ;; Alasvetovalikko yksikön nopeaa vaihtamista varten
      [:ul.dropdown-menu.livi-alasvetolista
       {:role "menu"
        :on-key-down #(kasittele-alasvetovalikon-toiminnot % {:id "alasveto-hallintayksikko"
                                                             :valittu-rivi valittu-rivi
                                                             :valinta-auki valinta-auki})}
       (for [muu-yksikko (filter #(not= % valittu) @hal/vaylamuodon-hallintayksikot)]
         ^{:key (str "hy-" (:id muu-yksikko))}
         [:li.harja-alasvetolistaitemi
          {:tabIndex "0"
           :on-key-down (fn [event]
                          (let [alasvedon-nappi (.querySelector (.getElementById js/document "alasveto-hallintayksikko") "button")]
                            (when (dom/enter-nappain? event)
                              (do
                                (.preventDefault event)
                                (.stopPropagation event)
                                (reset! valinta-auki nil)
                                (nav/valitse-hallintayksikko! muu-yksikko)
                                (r/after-render (fn [] (.focus alasvedon-nappi)))))))}
          [linkki (hal/elynumero-ja-nimi muu-yksikko)
           #(do (reset! valinta-auki nil)
              (nav/valitse-hallintayksikko! muu-yksikko))]])]]]))

(def urakka-rivi (atom nil))

(defn urakka [valinta-auki]
  (when @nav/valittu-hallintayksikko
    (let [valittu @nav/valittu-urakka]
      [:li.murupolkuvalitsin
       [:label {:for "alasveto-urakka"} "Urakka"]
       [:div.dropdown.livi-alasveto {:id "alasveto-urakka"
                                    :class (when (= :urakka @valinta-auki) "open")}
        [:span.valittu-urakka.murupolkuteksti (or (:nimi valittu) "- Urakka -") " "]

        [:button.nappi-murupolkualasveto.dropdown-toggle
         {:on-click #(swap! valinta-auki
                       (fn [v]
                         (if (= v :urakka)
                           nil
                           :urakka)))
          :on-key-down #(kasittele-toggle-napin-toiminnot % {:id "alasveto-urakka"
                                                             :valinta-auki valinta-auki
                                                             :valikon-tieto :urakka})}
         (if (= :urakka @valinta-auki)
           [:span.livicon-chevron-up]
           [:span.livicon-chevron-down])]

        ;; Alasvetovalikko urakan nopeaa vaihtamista varten
        [:ul.urakkalista.dropdown-menu.livi-alasvetolista
         {:role "menu"
          :on-key-down #(kasittele-alasvetovalikon-toiminnot % {:id "alasveto-urakka"
                                                               :valittu-rivi urakka-rivi
                                                               :valinta-auki valinta-auki})}

         (let [muut-kaynnissaolevat-urakat (sort-by :nimi
                                             (filter #(and
                                                        (not= % valittu)
                                                        (pvm/jalkeen? (:loppupvm %) (pvm/nyt)))
                                               @nav/suodatettu-urakkalista))]
           (if (empty? muut-kaynnissaolevat-urakat)
             [alasveto-ei-loydoksia "Tästä hallintayksiköstä ei löydy muita urakoita, joita on oikeus tarkastella."]

             (for [urakka muut-kaynnissaolevat-urakat]
               ^{:key (str "urakka-" (:id urakka))}
               [:li.harja-alasvetolistaitemi
                {:tabIndex "0"
                 :on-key-down #(when (dom/enter-nappain? %)
                                 (do
                                   (.preventDefault %)
                                   (.stopPropagation %)
                                   (reset! valinta-auki nil)
                                   (reset! urakka-rivi nil)
                                   (nav/valitse-urakka! urakka)
                                   (.focus (.querySelector (.getElementById js/document "alasveto-urakka") "button"))))}
                [linkki (:nimi urakka) #(nav/valitse-urakka! urakka)]])))]]])))

(defn urakoitsija []
  [:div.murupolku-urakoitsija
   [:label {:for "alasveto-urakoitsija"} "Urakoitsija"]
   [livi-pudotusvalikko {:elementin-id "alasveto-urakoitsija"
                         :valinta @nav/valittu-urakoitsija
                         :format-fn #(if % (:nimi %) "Kaikki")
                         :valitse-fn nav/valitse-urakoitsija!
                         :class (str "alasveto-urakoitsija"
                                     (when (boolean @nav/valittu-urakka) " disabled"))
                         :disabled (or (some? @nav/valittu-urakka)
                                       (= (:sivu @reitit/url-navigaatio) :raportit))}
    (vec (conj (into [] (case (:arvo @nav/urakkatyyppi)
                          :kaikki @urakoitsijat/urakoitsijat-kaikki
                          :hoito @urakoitsijat/urakoitsijat-hoito
                          :paallystys @urakoitsijat/urakoitsijat-paallystys
                          :tiemerkinta @urakoitsijat/urakoitsijat-tiemerkinta
                          :valaistus @urakoitsijat/urakoitsijat-valaistus
                          :vesivayla @urakoitsijat/urakoitsijat-vesivaylat

                          @urakoitsijat/urakoitsijat-hoito)) ;;defaulttina hoito
               nil))]])

(defn urakkatyyppi []
  [:div.murupolku-urakkatyyppi
   [:label {:for "alasveto-urakkatyyppi"} "Urakkatyyppi"]
   [livi-pudotusvalikko {:elementin-id "alasveto-urakkatyyppi"
                         :valinta @nav/urakkatyyppi
                         :format-fn #(if % (:nimi %) "Kaikki")
                         :valitse-fn nav/vaihda-urakkatyyppi!
                         :class (str "alasveto-urakkatyyppi" (when (boolean @nav/valittu-urakka) " disabled"))
                         :disabled (boolean @nav/valittu-urakka)
                         :data-cy "murupolku-urakkatyyppi"}
    nav/+urakkatyypit-ja-kaikki+]])

(defn murupolku
  "Itse murupolkukomponentti joka sisältää html:n"
  []
  (let [valinta-auki (atom nil)]
    (komp/luo
      (komp/kuuntelija
        [:hallintayksikko-valittu :hallintayksikkovalinta-poistettu
         :urakka-valittu :urakkavalinta-poistettu]
        #(reset! valinta-auki false)
        :body-klikkaus
        (fn [this {klikkaus :tapahtuma}]
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
                 :class (str "murupolku "
                          (when (empty? @nav/tarvitsen-isoa-karttaa)
                            (if @nav/murupolku-nakyvissa?
                              ""
                              "hide")))}
           [:ol.col-sm-7.murupolku-vasen
            [koko-maa]
            [hallintayksikko valinta-auki]
            [urakka valinta-auki]]
           (when ei-urakkaa?
             [:div.col-sm-5.murupolku-oikea
              [:div
               [urakkatyyppi]
               (when-not urakoitsija?
                 [urakoitsija])]])])))))
