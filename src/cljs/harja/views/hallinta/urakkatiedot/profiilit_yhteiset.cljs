(ns harja.views.hallinta.urakkatiedot.profiilit-yhteiset
  (:require [harja.ui.yleiset :refer [pudotusvalikko]]
            [harja.tiedot.hallinta.urakkatiedot.sanktio-profiilit-tiedot :as sanktio-tiedot]))

(defn suodatin-rivi [e! paivita-suodatin suodattimet profiilit]
  (let [{:keys [teksti urakkatyyppi aktiivisuus]} suodattimet
        urakkatyypit (->> profiilit (map :urakkatyyppi) distinct sort)
        urakkatyyppi-vaihtoehdot (into [{:nimi "Kaikki" :arvo :kaikki}]
                                  (map (fn [t] {:nimi (sanktio-tiedot/urakkatyyppi-teksti t) :arvo t})
                                    urakkatyypit))
        aktiivisuus-vaihtoehdot [{:nimi "Kaikki" :arvo :kaikki}
                                 {:nimi "Aktiiviset" :arvo :aktiiviset}
                                 {:nimi "Passiiviset" :arvo :passiiviset}]
        valittu-urakkatyyppi (some #(when (= (:arvo %) urakkatyyppi) %) urakkatyyppi-vaihtoehdot)
        valittu-aktiivisuus (some #(when (= (:arvo %) aktiivisuus) %) aktiivisuus-vaihtoehdot)]
    [:div.row
     [:div.col-md-4
      [:div.label-ja-alasveto
       [:label.alasvedon-otsikko "Hae nimellä"]
       [:input.input-default
        {:type "text"
         :value teksti
         :placeholder "Kirjoita profiilin nimi"
         :on-change #(e! (paivita-suodatin :teksti (-> % .-target .-value)))}]]]
     [:div.col-md-4
      [pudotusvalikko
       "Urakkatyyppi"
       {:valinta valittu-urakkatyyppi
        :format-fn :nimi
        :valitse-fn #(e! (paivita-suodatin :urakkatyyppi (:arvo %)))}
       urakkatyyppi-vaihtoehdot]]
     [:div.col-md-4
      [pudotusvalikko
       "Aktiivisuus"
       {:valinta valittu-aktiivisuus
        :format-fn :nimi
        :valitse-fn #(e! (paivita-suodatin :aktiivisuus (:arvo %)))}
       aktiivisuus-vaihtoehdot]]]))
