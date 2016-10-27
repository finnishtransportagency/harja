(ns harja-laadunseuranta.ui.kitkamittaus
  (:require [reagent.core :as reagent :refer [atom]])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(def +kursorin-leveys+ 28)

(defn- lisaa-numero [n {:keys [desimaalit max-length] :as model}]
  (if (< (count desimaalit) max-length)
    (assoc model :desimaalit (str desimaalit n))
    model))

(defn numeropainike [model n]
  (fn [_] (swap! model #(lisaa-numero n %))))

(defn poista-desimaalit [model]
  (assoc model :desimaalit (subs (:desimaalit model) 0 (:min-length model))))

(defn tyhjennyspainike [model]
  (fn [_] (swap! model poista-desimaalit)))

(defn laske-kursorin-paikka [desimaalit]
  (* +kursorin-leveys+ (count desimaalit)))

(defn- muunna-arvoksi [desimaalit]
  (js/parseFloat (clojure.string/replace desimaalit "," ".")))

(defn kitkamittaus [model mittaus-valmis]
  [:div.kitkamittaus
   [:div.syottokentta (str (:desimaalit @model))
    [:div.kursori {:style {:left (laske-kursorin-paikka (:desimaalit @model))}}]]
   [:div.nappaimisto
    [:button {:on-click (numeropainike model 7)} [:span#btn7 "7"]]
    [:button {:on-click (numeropainike model 8)} [:span#btn8 "8"]]
    [:button {:on-click (numeropainike model 9)} [:span#btn9 "9"]]
    [:button {:on-click (numeropainike model 4)} [:span#btn4 "4"]]
    [:button {:on-click (numeropainike model 5)} [:span#btn5 "5"]]
    [:button {:on-click (numeropainike model 6)} [:span#btn6 "6"]]
    [:button {:on-click (numeropainike model 1)} [:span#btn1 "1"]]
    [:button {:on-click (numeropainike model 2)} [:span#btn2 "2"]]
    [:button {:on-click (numeropainike model 3)} [:span#btn3 "3"]]
    [:button#delbtn {:on-click (tyhjennyspainike model)} [:span#del  [:span.livicon-undo]]]
    [:button {:on-click (numeropainike model 0)} [:span#btn0  "0"]]
    [:button#okbtn {:disabled (empty? (:desimaalit @model))}
     [:span#ok {:on-click #(when-not (empty? (:desimaalit @model))
                             (mittaus-valmis (muunna-arvoksi (:desimaalit @model)))
                             (swap! model poista-desimaalit))} [:span.livicon-check]]]]])

(defn kitkamittauskomponentti [mittaus-valmis]
  [kitkamittaus (atom {:desimaalit "0,"
                       :min-length 2
                       :max-length 4}) mittaus-valmis])

(def testimodel (atom {:desimaalit "0,"
                       :min-length 2
                       :max-length 4}))

(defcard kitkamittaus-card
  (fn [model _]
    (reagent/as-element [kitkamittaus model #(js/window.alert (str "Mittaus valmis: " %))]))
  testimodel
  {:watch-atom true
   :inspect-data true})
