(ns harja.tiedot.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r]
            [harja.pvm :as pvm]
            [harja.domain.paikkaus :as paikkaus]))

(defonce tila (atom {:paikkauskohde-id nil
                     :aloitus-aikavali (pvm/aikavali-nyt-miinus 7)}))

(defn ensimmaisen-haun-kasittely
  [{:keys [paikkauskohde-idn-polku tuloksen-avain kasittele-haettu-tulos tulos app]}]
  (let [paikkauskohde-id (:paikkauskohde-id @tila)
        paikkauskohteet (map #(identity
                                {:id (::paikkaus/id %)
                                :nimi (::paikkaus/nimi %)
                                :valittu? (or (nil? paikkauskohde-id)
                                              (= paikkauskohde-id
                                                 (::paikkaus/id %)))})
                             (:paikkauskohteet tulos))
        naytettavat-tulokset (filter #(or (nil? paikkauskohde-id)
                                          (= paikkauskohde-id
                                             (get-in % paikkauskohde-idn-polku)))
                                     (tuloksen-avain tulos))
        naytettavat-tiedot (kasittele-haettu-tulos naytettavat-tulokset app)]
    (swap! tila assoc
           :paikkauskohde-id nil
           :aloitus-aikavali (pvm/aikavali-nyt-miinus 7))
    (-> app
        (merge naytettavat-tiedot)
        (assoc-in [:valinnat :urakan-paikkauskohteet] paikkauskohteet)
        (assoc-in [:valinnat :tyomenetelmat] (:tyomenetelmat tulos))
        (assoc :paikkauksien-haku-kaynnissa? false
               :ensimmainen-haku-tehty? true))))