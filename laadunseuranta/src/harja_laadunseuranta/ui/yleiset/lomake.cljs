(ns harja-laadunseuranta.ui.yleiset.lomake
  "Yleisiä lomake-apureita"
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.havaintolomake :refer [tallenna-lomake!
                                                                peruuta-lomake!]]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.ui.yleiset.napit :refer [nappi]]
            [cljs-time.format :as time-fmt]
            [harja-laadunseuranta.ui.yleiset.varmistusdialog :as varmistusdialog]
            [harja-laadunseuranta.ui.yleiset.yleiset :as yleiset]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja.domain.tierekisteri :as tr-domain])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

;; Lomakkeessa käytettävät kentät

(defn tr-osoite [{:keys [tr-osoite-atom virheet-atom liittyva-havainto]}]
  ;; FIXME Jos liittyvä havainto otetaan pois, pitäisi lomakkeella olla jälleen ehdolla
  ;; nykyinen tieosoite. Nyt osoitteeksi jää liittyvän havainnon osoite
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
                                  (swap! virheet-atom conj :tr-osoite-virheellinen)))
        tarkista-liittyva-havainto! (fn [tr-osoite-atom liittyva-havainto]
                                      (when liittyva-havainto
                                        (reset! tr-osoite-atom (:tr-osoite liittyva-havainto))))]
    (fn [{:keys [tr-osoite-atom virheet-atom liittyva-havainto]}]
      (tarkista-virheet! tr-osoite-atom virheet-atom)
      (tarkista-liittyva-havainto! tr-osoite-atom liittyva-havainto)
      (let [{:keys [tie aosa aet losa let]} @tr-osoite-atom
            muokattava? (some? liittyva-havainto)]
        [:div.tr-osoite
         [:input {:type "number" :value tie :on-change #(on-change % :tie) :placeholder "Tie#"
                  :disabled muokattava?}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value aosa :on-change #(on-change % :aosa) :placeholder "aosa"
                  :disabled muokattava?}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value aet :on-change #(on-change % :aet) :placeholder "aet"
                  :disabled muokattava?}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value losa :on-change #(on-change % :losa) :placeholder "losa"
                  :disabled muokattava?}]
         [:span.valiviiva " / "]
         [:input {:type "number" :value let :on-change #(on-change % :let) :placeholder "let"
                  :disabled muokattava?}]]))))

(defn pvm-aika [aika]
  [:div.pvm-aika
   [:input {:type "date"
            :value (fmt/pvm @aika)
            :on-change #()
            :name "pvm"}]
   [:input {:type "time"
            :value (fmt/klo @aika)
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


(defn- liittyva-havainto-komp
  [{:keys [liittyva-havainto lomake-liittyy-havaintoon-atom
           havainnon-tiedot-avaimella liittyy-varmasti-tiettyyn-havaintoon?]}]
  (let [aktiivinen-havainto? (= (:id liittyva-havainto)
                                @lomake-liittyy-havaintoon-atom)]
    [:div {:class (str "liittyva-havainto "
                       (when aktiivinen-havainto?
                         "liittyva-havainto-aktiivinen"))
           :on-click (when-not liittyy-varmasti-tiettyyn-havaintoon?
                       (fn []
                         (if (= (:id liittyva-havainto)
                                @lomake-liittyy-havaintoon-atom)
                           (reset! lomake-liittyy-havaintoon-atom nil)
                           (reset! lomake-liittyy-havaintoon-atom
                                   (:id liittyva-havainto)))))}
     [kuvat/svg-sprite (:ikoni (havainnon-tiedot-avaimella
                                 (:havainto-avain liittyva-havainto)))]
     ^{:key (hash liittyva-havainto)}
     [:div.liittyva-havainto-tiedot
      [:div
       [:span.nimi (:nimi (havainnon-tiedot-avaimella
                            (:havainto-avain liittyva-havainto)))]]
      [:div
       [:span.aika (fmt/klo (:aikaleima liittyva-havainto))]
       [:span " "]
       [:span.tr-osoite (when (:tr-osoite liittyva-havainto)
                          (str "(" (tr-domain/tierekisteriosoite-tekstina
                                     (:tr-osoite liittyva-havainto)
                                     {:teksti-tie? false
                                      :teksti-ei-tr-osoitetta? false}) ")"))]]]]))

(defn liittyvat-havainnot [{:keys [havainnot-ryhmittain liittyy-varmasti-tiettyyn-havaintoon?]}]
  (let [kaikki-havainnot (into [] (apply concat (vals havainnot-ryhmittain)))
        havainnon-tiedot-avaimella (fn [avain]
                                     (first
                                       (filter
                                         #(= (:avain %) avain)
                                         kaikki-havainnot)))]
    (fn [{:keys [liittyvat-havainnot
                 lomake-liittyy-havaintoon-atom]}]
      [:div.liittyvat-havainnot-container
       [:div.liittyvat-havainnot
        (doall
          (if-not liittyy-varmasti-tiettyyn-havaintoon?
            (for [liittyva-havainto liittyvat-havainnot]
              ^{:key (:id liittyva-havainto)}
              [liittyva-havainto-komp {:liittyva-havainto liittyva-havainto
                                       :lomake-liittyy-havaintoon-atom lomake-liittyy-havaintoon-atom
                                       :havainnon-tiedot-avaimella havainnon-tiedot-avaimella}])
            (when-let [valittu-havainto (first (filter #(= (:id %)) liittyvat-havainnot))]
              [liittyva-havainto-komp {:liittyva-havainto valittu-havainto
                                       :lomake-liittyy-havaintoon-atom lomake-liittyy-havaintoon-atom
                                       :havainnon-tiedot-avaimella havainnon-tiedot-avaimella
                                       :liittyy-varmasti-tiettyyn-havaintoon? liittyy-varmasti-tiettyyn-havaintoon?}])))]
       [:div.jatkuvat-havainnot-vihje
        [yleiset/vihje "Jos et valitse mitään, lomake kirjataan yleisenä havaintona."]]])))

;; Lomakkeen osat

(defn lomake [{:keys [tallenna-fn peruuta-fn otsikko lomake-koskettu-atom
                      lomakedata-atom lomake-virheet-atom]} & sisalto]
  (let [tila-alussa @lomakedata-atom
        tarkista-lomake-koskettu! (fn [tila-alussa nykyinen-tila]
                                    (when (not= tila-alussa nykyinen-tila)
                                      (.log js/console "Lomake koskettu!")
                                      (reset! lomake-koskettu-atom true)))]
    (fn [{:keys [tallenna-fn peruuta-fn otsikko
                 lomakedata-atom lomake-virheet-atom]} & sisalto]
      (tarkista-lomake-koskettu! tila-alussa @lomakedata-atom)

      [:div.lomake-container
       [:div.lomake-sisalto-container
        [:div.lomake-title otsikko]
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
                           :on-click
                           #(if @lomake-koskettu-atom
                              (varmistusdialog/varmista! "Hylätäänkö lomake?"
                                                         {:positiivinen-vastaus-teksti "Kyllä"
                                                          :negatiivinen-vastaus-teksti "Ei"
                                                          :positiivinen-fn peruuta-fn})
                              (peruuta-fn))}]]]])))

(defn rivi [& elementit]
  ^{:key (hash elementit)}
  [:div.lomake-rivi
   (doall (for [elementti elementit]
            ^{:key (hash elementti)}
            elementti))])

(defn kentta [label komponentti]
  ^{:key label}
  [:div.lomake-kentta
   [:div.label label]
   komponentti])

