(ns harja.ui.yleiset
  "Yleisiä UI komponentteja ja apureita"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.loki :refer [log tarkkaile!]]
            [harja.asiakas.tapahtumat :as t]))

(defonce korkeus (atom (-> js/document .-body .-clientHeight)))
(defonce leveys (atom (-> js/document .-body .-clientWidth)))

(defonce koon-kuuntelija (do (set! (.-onresize js/window)
                                   (fn [_]
                                     (reset! korkeus (-> js/document .-body .-clientHeight))
                                     (reset! leveys (-> js/document .-body .-clientWidth))))
                             true))

(tarkkaile! "korkeus" korkeus)
(tarkkaile! "leveys" leveys)
  
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


;;
(defn linkki [otsikko toiminto]
  [:a {:href "#" :on-click #(do (.preventDefault %) (toiminto))} otsikko])

(defn alasvetovalinta [_ vaihtoehdot]
  (let [auki (atom false)]
    (fn [{:keys [valinta format-fn valitse-fn class]} vaihtoehdot]
      [:div.dropdown {:class (str class " " (when @auki "open"))}
         [:button.btn.btn-default {:type "button"
                                   :on-click #(do 
                                               (swap! auki not)
                                               nil)} ;; Reactin mielestä ei kiva palauttaa booleania handleristä
          [:span.valittu (format-fn valinta)] 
          " " [:span.caret]]
           [:ul.dropdown-menu
            (for [vaihtoehto vaihtoehdot]
              ^{:key (hash vaihtoehto)}
              [:li (linkki (format-fn vaihtoehto) #(do (valitse-fn vaihtoehto)
                                                     (reset! auki false)
                                                     ))])
                   ]])))

(defn radiovalinta [otsikko valinta valitse-fn & vaihtoehdot]
  
  (let [vaihda-tyyppi (fn [e] (valitse-fn (keyword (.-value (.-target e)))))]
       
       [:div.btn-group.pull-right.murupolku-radiovalinta
        [:div otsikko " "]
         (for [[otsikko arvo] (partition 2 vaihtoehdot)] 
           [:label.btn.btn-primary
                   [:input {:type "radio" :value (name arvo) :on-change vaihda-tyyppi 
                            :checked (if (= arvo valinta) true false)} " " otsikko]])]))
(defn kuuntelija
  "Lisää komponentille käsittelijät tietyille tapahtuma-aiheille.
Toteuttaa component-did-mount ja component-will-unmount elinkaarimetodit annetulle komponentille.
aiheet-ja-kasittelijat on vuorotellen aihe (yksi avainsana tai joukko avainsanoja) ja käsittelyfunktio,
jolle annetaan yksi parametri (komponentti). Alkutila on komponentin inital-state."
  [alkutila render-fn & aiheet-ja-kasittelijat]
  (let [kuuntelijat (partition 2 aiheet-ja-kasittelijat)]
    (reagent/create-class
     {:get-initial-state (fn [this] alkutila)
      :render (fn [this] (render-fn this))
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
                             
    
