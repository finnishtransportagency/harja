(ns harja.ui.varmista-kayttajalta
  "Modaali jossa käyttäjältä varmistetaan tehdäänkö toiminto"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]))

(defn varmista-kayttajalta [{:keys [otsikko sisalto toiminto-fn hyvaksy napit]}]
  "Suorittaa annetun toiminnon vain, jos käyttäjä hyväksyy sen.

  Parametrimap:
  :otsikko = dialogin otsikko
  :sisalto = dialogin sisältö
  :hyvaksy = hyväksyntäpainikkeen teksti tai elementti
  :toiminto-fn = varsinainen toiminto, joka ajetaan käyttäjän hyväksyessä
  :napit = Vektori, joka määrittelee footeriin asetettavat napit. Vaihtoehtoja ovat :peruuta, :hyvaksy, :takaisin, :poista."
  (let [napit (or napit [:peruuta :hyvaksy])]
    (modal/nayta! {:otsikko otsikko
                   :footer [:span
                            (doall
                              (for [tyyppi napit]
                                (with-meta
                                  (case tyyppi
                                    :peruuta [napit/peruuta "Peruuta" #(modal/piilota!)]
                                    :hyvaksy [napit/hyvaksy hyvaksy #(do
                                                                       (modal/piilota!)
                                                                       (toiminto-fn))]
                                    :takaisin [napit/takaisin "Peruuta" #(modal/piilota!)]
                                    :poista [napit/poista hyvaksy #(do
                                                                     (modal/piilota!)
                                                                     (toiminto-fn))]
                                    nil)
                                  {:key (str "varmistus-nappi-" tyyppi)})))]}
                  sisalto)))