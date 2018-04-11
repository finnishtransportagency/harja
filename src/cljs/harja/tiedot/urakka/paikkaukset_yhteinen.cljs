(ns harja.tiedot.urakka.paikkaukset-yhteinen
  (:require [reagent.core :as r]
            [harja.pvm :as pvm]
            [harja.domain.paikkaus :as paikkaus]))

(defonce paikkauskohde-id (atom nil))

(defn ensimmaisen-haun-kasittely
  [{:keys [paikkauskohde-idn-polku tuloksen-avain kasittele-haettu-tulos tulos app]}]
  (let [id @paikkauskohde-id
        paikkauskohteet (map #(identity
                                {:id (::paikkaus/id %)
                                :nimi (::paikkaus/nimi %)
                                :valittu? (or (nil? id)
                                              (= id
                                                 (::paikkaus/id)))})
                             (:paikkauskohteet tulos))
        naytettavat-tulokset (filter #(or (nil? id)
                                          (= id
                                             (get-in % paikkauskohde-idn-polku)))
                                     (tuloksen-avain tulos))
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