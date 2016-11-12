(ns harja-laadunseuranta.ui.lomake
  "Yleisiä lomake-apureita"
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.havaintolomake :refer [tallenna-lomake!
                                                                peruuta-lomake!]]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [cljs-time.format :as time-fmt])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn- parse-and-check-value [s min max]
  (let [val (js/parseFloat s)]
    (if (js/isNaN val)
      nil
      (when (<= min val max)
        val))))

(defn tr-osoite [{:keys [tie aosa aet losa let] :as osoite}]
  [:div.tr-osoite
   [:input {:type "text" :value tie :on-change #() :placeholder "Tie#"}] [:span.valiviiva " / "]
   [:input {:type "text" :value aosa :on-change #() :placeholder "aosa"}] [:span.valiviiva " / "]
   [:input {:type "text" :value aet :on-change #() :placeholder "aet"}] [:span.valiviiva " / "]
   [:input {:type "text" :value losa :on-change #() :placeholder "losa"}] [:span.valiviiva " / "]
   [:input {:type "text" :value let :on-change #() :placeholder "let"}]])

(defn pvm-aika [aika]
  [:div.pvm-aika
   [:input {:type "text"
            :value (time-fmt/unparse fmt/pvm-fmt @aika)
            :on-change #()
            :name "pvm"}]
   [:input {:type "text"
            :value (time-fmt/unparse fmt/klo-fmt @aika)
            :on-change #()
            :name "klo"}]])

(defn input-kentta [nimi validointivirheita model {:keys [step min max]}]
  (let [arvo (atom (str @model))
        validi (atom true)]
    (fn [_ _]
      [:div
       [:input {:type "number"
                :step step
                :min min
                :max max
                :style {:width "105px"
                        :display "block"}
                :value @arvo
                :on-blur #(when @validi (reset! model (parse-and-check-value @arvo min max)))
                :on-change #(let [v (-> % .-target .-value)]
                             (reset! arvo v)
                             (if-not (empty? v)
                               (let [x (not (nil? (parse-and-check-value v min max)))]
                                 (reset! validi x)
                                 (swap! validointivirheita (if x disj conj) nimi))
                               (do (reset! validi true)
                                   (swap! validointivirheita disj nimi))))}]
       (when-not @validi [:div.validointivirhe "Arvo ei validi"])])))

(defn kentta [label komponentti]
  [:div.lomake-kentta
   [:div.label label]
   komponentti])

(defn- tekstialue [teksti]
  [:textarea {:rows 5
              :on-change #(reset! teksti (-> % .-target .-value))
              :defaultValue ""
              :style {:resize "none"}}])