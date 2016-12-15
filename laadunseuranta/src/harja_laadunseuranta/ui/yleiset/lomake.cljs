(ns harja-laadunseuranta.ui.yleiset.lomake
  "Yleisiä lomake-apureita"
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.havaintolomake :refer [tallenna-lomake!
                                                                peruuta-lomake!]]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.ui.yleiset.napit :refer [nappi]]
            [cljs-time.format :as time-fmt]
            [harja-laadunseuranta.ui.yleiset.yleiset :as yleiset]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn- parse-and-check-value [s min max]
  (let [val (js/parseFloat s)]
    (if (js/isNaN val)
      nil
      (when (<= min val max)
        val))))

(defn tr-osoite [tr-osoite-atom virheet-atom]
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
                          (swap! tr-osoite-atom assoc avain (js/parseInt arvo-tekstina))))))
        tarkista-virheet! (fn [tr-osoite-atom virheet-atom]
                            (cond (and (nil? (:tie @tr-osoite-atom))
                                       (nil? (:aosa @tr-osoite-atom))
                                       (nil? (:aet @tr-osoite-atom))
                                       (nil? (:losa @tr-osoite-atom))
                                       (nil? (:let @tr-osoite-atom)))
                                  (swap! virheet-atom disj :tr-osoite-virheellinen)

                                  (and (some? (:tie @tr-osoite-atom))
                                       (some? (:aosa @tr-osoite-atom))
                                       (some? (:aet @tr-osoite-atom))
                                       (nil? (:losa @tr-osoite-atom))
                                       (nil? (:let @tr-osoite-atom)))
                                  (swap! virheet-atom disj :tr-osoite-virheellinen)

                                  (and (some? (:tie @tr-osoite-atom))
                                       (some? (:aosa @tr-osoite-atom))
                                       (some? (:aet @tr-osoite-atom))
                                       (some? (:losa @tr-osoite-atom))
                                       (some? (:let @tr-osoite-atom)))
                                  (swap! virheet-atom disj :tr-osoite-virheellinen)

                                  :default
                                  (swap! virheet-atom conj :tr-osoite-virheellinen)))]
    (fn [tr-osoite-atom]
      (tarkista-virheet! tr-osoite-atom virheet-atom)
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

(defn tekstialue [teksti]
  [:textarea {:rows 5
              :on-change #(reset! teksti (-> % .-target .-value))
              :defaultValue ""
              :style {:resize "none"}}])

(defn checkbox [nimi arvo-atom]
  [:div
   [:input {:id nimi
            :type "checkbox"
            :on-change #(swap! arvo-atom not)}]
   [:label {:for nimi} nimi]])

(defn liittyvat-havainnot [liittyvat-havainnot]
  [:div.liittyvat-havainnot
   [:ul]
   (doall (for [liittyva-havainto liittyvat-havainnot]
            ^{:key (:id liittyva-havainto)}
            [:li (:havainto-avain liittyva-havainto)]))
   [yleiset/vihje "Jos et valitse mitään, lomake kirjataan yleisenä havaintona."]])

;; Lomakkeen osat

(defn lomake [{:keys [tallenna-fn peruuta-fn otsikko
                      lomakedata-atom lomake-virheet-atom]} & sisalto]
  [:div.lomake-container
   [:div.lomake-title otsikko]
   ;; FIXME Miksi tulee unique key error?
   (doall (for [elementti sisalto]
            ^{:key (hash elementti)}
            elementti))
   [:footer.lomake-footer
    [nappi "Tallenna" {:on-click (fn []
                                   (.log js/console. "Tallenna. Virheet: " (pr-str @lomake-virheet-atom))
                                   (when (empty? @lomake-virheet-atom)
                                     (tallenna-fn @lomakedata-atom)))
                       :disabled (not (empty? @lomake-virheet-atom))
                       :luokat-str (str "nappi-myonteinen "
                                        (when-not (empty? @lomake-virheet-atom)
                                          "nappi-disabloitu"))
                       :ikoni (kuvat/svg-sprite "tallenna-18")}]
    [nappi "Peruuta" {:luokat-str "nappi-kielteinen"
                      :on-click peruuta-fn}]]])

(defn rivi [& elementit]
  [:div.lomake-rivi
   (doall (for [elementti elementit]
            ^{:key (hash elementti)}
            elementti))])

(defn kentta [label komponentti]
  [:div.lomake-kentta
   [:div.label label]
   komponentti])

