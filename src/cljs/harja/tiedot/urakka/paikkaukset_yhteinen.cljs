(ns harja.tiedot.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r]))

(defonce paikkauskohde-id (atom nil))

(defn ensimmaisen-haun-kasittely
  [{:keys [paikkauskohde-idn-polku paikkauskohde-nimen-polku kasittele-haettu-tulos tulos app]}]
  (let [id @paikkauskohde-id
        paikkauskohteet (reduce (fn [paikkaukset paikkaus]
                                  (if (some #(= (:id %) (get-in paikkaus paikkauskohde-idn-polku))
                                            paikkaukset)
                                    paikkaukset
                                    (conj paikkaukset
                                          {:id (get-in paikkaus paikkauskohde-idn-polku)
                                           :nimi (get-in paikkaus paikkauskohde-nimen-polku)
                                           :valittu? (or (nil? id)
                                                         (= id
                                                            (get-in paikkaus paikkauskohde-idn-polku)))})))
                                [] tulos)
        naytettavat-tulokset (filter #(or (nil? id)
                                          (= id
                                             (get-in % paikkauskohde-idn-polku)))
                                     tulos)
        naytettavat-tiedot (kasittele-haettu-tulos naytettavat-tulokset app)]
    (reset! paikkauskohde-id nil)
    (-> app
        (merge naytettavat-tiedot)
        (assoc :paikkauksien-haku-kaynnissa? false)
        (assoc-in [:valinnat :urakan-paikkauskohteet] paikkauskohteet))))

(defn valinta-wrap [app paivitys-fn polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (paivitys-fn {polku u}))))