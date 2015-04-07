(ns harja.ui.yleiset
  "Yleisiä UI komponentteja ja apureita"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.loki :refer [log tarkkaile!]]
            [harja.asiakas.tapahtumat :as t]))

(declare kuuntelija)

(defonce korkeus (atom (-> js/window .-innerHeight)))
(defonce leveys (atom (-> js/window .-innerWidth)))
;;(defonce sisallon-korkeus (atom (-> js/document .-body .-clientHeight)))


(defonce koon-kuuntelija (do (set! (.-onresize js/window)
                                   (fn [_]
                                     (reset! korkeus (-> js/window .-innerHeight))
                                     (reset! leveys (-> js/window .-innerWidth))
                                     ))
                             true))

;;(defonce sisallon-koon-kuuntelija (do
;;                                    (js/setInterval #(reset! sisallon-korkeus (-> js/document .-body .-clientHeight)) 200)
;;                                    true))

(defn ajax-loader
  "Näyttää latausanimaatiokuvan ja optionaalisen viestin."
  ([] (ajax-loader nil))
  ([viesti]
     [:div.ajax-loader
      [:img {:src "/images/ajax-loader.gif"}]
      (when viesti
        [:div.viesti viesti])]))


(defn sisalla? 
  "Tarkistaa onko annettu tapahtuma tämän React komponentin sisällä."
  [komponentti tapahtuma]
  (let [dom (reagent/dom-node komponentti)
        elt (.-target tapahtuma)]
    (loop [ylempi (.-parentNode elt)]
      (if (or (nil? ylempi)
              (= ylempi js/document.body))
        false
        (if (= dom ylempi)
          true
          (recur (.-parentNode ylempi)))))))


(defn nuolivalinta
  "Tekee handlerin, joka helpottaa nuolivalinnan tekemistä. Ottaa kolme funktiota: ylös, alas ja enter, 
joita kutsutaan kun niiden näppäimiä paineetaan."
  [ylos alas enter]
  #(let [kc (.-keyCode %)]
     (when (or (= kc 38)
               (= kc 40)
               (= kc 13))
       (.preventDefault %)
       (case kc
         38 ;; nuoli ylös
         (ylos)

         40 ;; nuoli alas
         (alas)

         13 ;; enter
         (enter)))))

  
;;
(defn linkki [otsikko toiminto]
  [:a {:href "#" :on-click #(do (.preventDefault %) (toiminto))} otsikko])


(defn raksiboksi [teksti checked toiminto info-teksti nayta-infoteksti?]
  [:span
  [:div.raksiboksi.input-group
   [:span.input-group-addon
    [:input {:type "checkbox"
             :checked (if checked "checked" "")
             :on-change #(do (.preventDefault %) (toiminto))}]
    ]
   [:label.form-control {:on-click #(toiminto)}  teksti]]
  
   (when nayta-infoteksti?
     info-teksti)])

(defn alasveto-ei-loydoksia [teksti]
  [:div.alasveto-ei-loydoksia teksti])

(defn alasvetovalinta [_ vaihtoehdot]
  (kuuntelija
   {:auki (atom false)}

   (fn [{:keys [valinta format-fn valitse-fn class disabled]} vaihtoehdot]
     (let [auki (:auki (reagent/state (reagent/current-component)))]
       [:div.dropdown {:class (str class " " (when @auki "open"))}
        [:button.btn.btn-default
         {:type "button"
          :disabled (if disabled "disabled" "")
          :on-click #(do 
                       (swap! auki not)
                       nil)
          :on-key-down #(let [kc (.-keyCode %)]
                          (when (or (= kc 38)
                                    (= kc 40)
                                    (= kc 13))
                            (.preventDefault %)
                            (when-not (empty? vaihtoehdot)
                              (let [nykyinen-valittu-idx (loop [i 0]
                                                           (if (= i (count vaihtoehdot))
                                                             nil
                                                             (if (= (nth vaihtoehdot i) valinta)
                                                               i
                                                               (recur (inc i)))))]
                                (case kc
                                  38 ;; nuoli ylös
                                  (if (or (nil? nykyinen-valittu-idx)
                                          (= 0 nykyinen-valittu-idx))
                                    (valitse-fn (nth vaihtoehdot (dec (count vaihtoehdot))))
                                    (valitse-fn (nth vaihtoehdot (dec nykyinen-valittu-idx))))

                                  40 ;; nuoli alas
                                  (if (or (nil? nykyinen-valittu-idx)
                                          (= (dec (count vaihtoehdot)) nykyinen-valittu-idx))
                                    (valitse-fn (nth vaihtoehdot 0))
                                    (valitse-fn (nth vaihtoehdot (inc nykyinen-valittu-idx))))
                               
                                  13 ;; enter
                                  (reset! auki false))))))}
         [:span.valittu (format-fn valinta)] 
         " " [:span.caret]]
        [:ul.dropdown-menu
         (for [vaihtoehto vaihtoehdot]
           ^{:key (hash vaihtoehto)}
           [:li (linkki (format-fn vaihtoehto) #(do (valitse-fn vaihtoehto)
                                                    (reset! auki false)
                                                    ))])
         ]]))

   :body-klikkaus
   (fn [this {klikkaus :tapahtuma}]
     (when-not (sisalla? this klikkaus)
       (reset! (:auki (reagent/state this)) false)))
   ))

(defn radiovalinta [otsikko valinta valitse-fn disabled & vaihtoehdot]
  (let [vaihda-valinta (fn [e] (valitse-fn (keyword (.-value (.-target e)))))]
    [:div.btn-group.pull-right.murupolku-radiovalinta
     [:div otsikko " "]
     (for [[otsikko arvo] (partition 2 vaihtoehdot)]
       ^{:key (hash otsikko)}
       [:label.btn.btn-primary {:disabled (if disabled "disabled" "")}
        [:input {:type "radio" :value (name arvo) :on-change vaihda-valinta
                 :disabled (if disabled "disabled" "")
                 :checked (if (= arvo valinta) true false)} " " otsikko]])]))
(defn kuuntelija
  "Lisää komponentille käsittelijät tietyille tapahtuma-aiheille.
Toteuttaa component-did-mount ja component-will-unmount elinkaarimetodit annetulle komponentille.
aiheet-ja-kasittelijat on vuorotellen aihe (yksi avainsana tai joukko avainsanoja) ja käsittelyfunktio,
jolle annetaan kaksi parametria: komponentti ja tapahtuma. Alkutila on komponentin inital-state."
  [alkutila render-fn & aiheet-ja-kasittelijat]
  (let [kuuntelijat (partition 2 aiheet-ja-kasittelijat)]
    (reagent/create-class
     {:get-initial-state (fn [this] alkutila)
      :reagent-render render-fn
      :component-did-mount (fn [this _]
                             (loop [kahvat []
                                    [[aihe kasittelija] & kuuntelijat] kuuntelijat]
                               (if-not aihe
                                 (reagent/set-state this {::kuuntelijat kahvat})
                                 (recur (concat kahvat
                                                (doall (map #(t/kuuntele! % (fn [tapahtuma] (kasittelija this tapahtuma)))
                                                            (if (keyword? aihe)
                                                              [aihe]
                                                              (seq aihe)))))
                                        kuuntelijat))))
      :component-will-unmount (fn [this _]                                
                                (let [kuuntelijat (-> this reagent/state ::kuuntelijat)]
                                  (doseq [k kuuntelijat]
                                    (k))))})))
                             
    
(defn tietoja
  "Tekee geneerisen tietonäkymän. Optiot on tyhjä mäppi vielä, ehkä jotain classia sinne."
  [optiot & otsikot-ja-arvot]
  [:div.tietoja
   (for [[otsikko arvo] (partition 2 otsikot-ja-arvot)]
     ^{:key otsikko}
     [:div.tietorivi
      [:span.tietokentta otsikko]
      [:span.tietoarvo arvo]])])
