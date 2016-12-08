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

(defn tr-osoite [tr-osoite-atom]
  (let [max-merkkeja 7
        arvo-validi? (fn [arvo-tekstina]
                       (boolean (or (empty? arvo-tekstina)
                                    (and (<= (count arvo-tekstina) max-merkkeja)
                                         (>= (js/parseInt arvo-tekstina) 0)))))
        on-change (fn [syote avain]
                    (let [arvo-tekstina (-> syote .-target .-value)
                          arvo-tyhja? (empty? arvo-tekstina)]
                      (when (arvo-validi? arvo-tekstina)
                        (if arvo-tyhja?
                          (swap! tr-osoite-atom assoc avain nil)
                          (swap! tr-osoite-atom assoc avain (js/parseInt arvo-tekstina))))))]
    (fn [tr-osoite-atom]
      (let [{:keys [tie aosa aet losa let]} @tr-osoite-atom]
        [:div.tr-osoite
         [:input {:type "number" :value tie :on-change #(on-change % :tie) :placeholder "Tie#"}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value aosa :on-change #(on-change % :aosa) :placeholder "aosa"}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value aet :on-change #(on-change % :aet) :placeholder "aet"}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value losa :on-change #(on-change % :losa) :placeholder "losa"}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value let :on-change #(on-change % :let) :placeholder "let"}]]))))

(defn pvm-aika [aika]
  [:div.pvm-aika
   [:input {:type "date"
            :value (time-fmt/unparse fmt/pvm-fmt @aika)
            :on-change #()
            :name "pvm"}]
   [:input {:type "time"
            :value (time-fmt/unparse fmt/klo-fmt @aika)
            :on-change #()
            :name "klo"}]])

(defn kentta [label komponentti]
  [:div.lomake-kentta
   [:div.label label]
   komponentti])

(defn- tekstialue [teksti]
  [:textarea {:rows 5
              :on-change #(reset! teksti (-> % .-target .-value))
              :defaultValue ""
              :style {:resize "none"}}])