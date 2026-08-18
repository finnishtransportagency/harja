(ns harja.views.urakka.valikatselmus.yhteenveto.sanktiot-ja-bonukset
  (:require [harja.fmt :as fmt]
            [harja.views.urakka.valikatselmus.yhteenveto.luvut :as luvut]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]))


(defn osio-bonukset [{:keys [paatokset]}
                     {:keys [yhteenvedon-tiedot]}]
  (let [lupauspaatos (valikatselmus-tiedot/ota-paatos paatokset :lupaukset)
        lupausbonus (or (luvut/arvo-paatoksesta lupauspaatos :lupausbonus) 0)

        asiakastyytyvaisyysbonus (apply + (map (fn [bonus]
                                                 (if (= (:tyyppi bonus) "asiakastyytyvaisyysbonus")
                                                   (:rahasumma bonus)
                                                   0))
                                            (:bonukset yhteenvedon-tiedot)))

        muut-bonukset (apply + (map (fn [bonus]
                                      (if (not (contains? #{"asiakastyytyvaisyysbonus" "lupausbonus"} (:tyyppi bonus)))
                                        (:rahasumma bonus)
                                        0))
                                 (:bonukset yhteenvedon-tiedot)))]

    [:div.valikatselmus-yhteenveto.osio {:aria-live "polite"}
     [:h3 "Bonukset"]

     [:div.flex-row.summa-rivi-ylin
      [:span "Lupausbonus"]
      [:span (fmt/euro-opt false lupausbonus)]]

     [:div.flex-row.summa-rivi
      [:span "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta"]
      [:span (fmt/euro-opt false asiakastyytyvaisyysbonus)]]

     [:div.flex-row.summa-rivi
      [:span "Muut bonukset"]
      [:span (fmt/euro-opt false muut-bonukset)]]]))


(defn osio-sanktiot [{:keys [paatokset]}
                     {:keys [yhteenvedon-tiedot]}]
  (let [lupauspaatos (valikatselmus-tiedot/ota-paatos paatokset :lupaukset)
        lupaussanktio (or (luvut/arvo-paatoksesta lupauspaatos :lupaussanktio) 0)
        muut-sanktiot (apply + (map (fn [a]
                                      (if (not (contains? #{"lupaussanktio" "arvonvahennyssanktio"} (:sakkoryhma a)))
                                        (+ (:maara a) (:indeksikorjaus a))
                                        0))
                                 (:sanktiot yhteenvedon-tiedot)))]

    [:div.valikatselmus-yhteenveto.osio {:aria-live "polite"}
     [:h3 "Sanktiot"]

     [:div.flex-row.summa-rivi-ylin
      [:span "Lupaussanktio"]
      [:span (fmt/euro-opt false lupaussanktio)]]

     [:div.flex-row.summa-rivi-ylin
      [:span "Laskutus yli laskutusrajan -sanktiot"]
      ;; FIXME onkohan tämä nyt tavoitehinnan ylitys 
      [:span "mikähän tämä on, mate?"]]

     [:div.flex-row.summa-rivi
      [:span "Muut sanktiot"]
      [:span (fmt/euro-opt false muut-sanktiot)]]]))


(defn osio-hoidonjohtopalkkio [{:keys [paatokset]} _luvut]
  (let [hoidonjohtopalkkiopaatos (valikatselmus-tiedot/ota-paatos paatokset :hoidonjohtopalkkion-muutos)
        hoidonjohtopalkkion-muutos (or (luvut/arvo-paatoksesta hoidonjohtopalkkiopaatos :hoidonjohtopalkkio_muutos) 0)]

    [:div.valikatselmus-yhteenveto.osio {:aria-live "polite"}
     [:h3 "Hoidonjohtopalkkion muutos"]
     ;; Jos hoidonjohtopalkkio on positiivinen, niin se on urakoitsijan saatavia.
     ;; Jos hoitovuoden lopun tavoitehinta ilman indeksitarkastuksia on enemmmän kuin 5% suurempi kuin tarjouksen tavoitehinta
     ;; niin hoidonjohtopalkkiota muutetaan. Jos se ei ole muuttunut yli 5%, niin muutos on nolla ja silloin näytetään nollaa.
     (if (:id hoidonjohtopalkkiopaatos)
       (if (>= hoidonjohtopalkkion-muutos 0)
         [:div.flex-row.summa-rivi
          [:span "Hoidonjohtopalkkion muutos"]
          [:span (fmt/euro-opt false hoidonjohtopalkkion-muutos)]]

         [:div.flex-row.summa-rivi
          [:span "Hoidonjohtopalkkion muutos"]
          [:span (fmt/euro-opt false 0)]])

       [:div.flex-row.summa-rivi
        [:span "Hoidonjohtopalkkion muutos"]
        [:span (fmt/euro-opt false 0)]])]))
