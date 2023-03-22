(ns harja.ui.varmista-kayttajalta
  "Modaali jossa käyttäjältä varmistetaan tehdäänkö toiminto"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]))

(defn varmista-kayttajalta "Suorittaa annetun toiminnon vain, jos käyttäjä hyväksyy sen.

  Parametrimap:
  :otsikko = dialogin otsikko
  :sisalto = dialogin sisältö
  :hyvaksy = hyväksyntäpainikkeen teksti tai elementti
  :peruuta-txt = peruuta-painikkeen teksti
  :toiminto-fn = varsinainen toiminto, joka ajetaan käyttäjän hyväksyessä
  :napit = Vektori, joka määrittelee footeriin asetettavat napit. Vaihtoehtoja ovat :peruuta, :hyvaksy, :takaisin, :poista."
  [{:keys [otsikko sisalto toiminto-fn hyvaksy peruuta-txt napit modal-luokka content-tyyli body-tyyli]}]
  (let [napit (or napit [:hyvaksy :peruuta])]
    (modal/nayta! {:otsikko otsikko
                   :footer [:span
                            (doall
                              (for [tyyppi napit]
                                (with-meta
                                  (case tyyppi
                                    :hyvaksy [napit/hyvaksy hyvaksy #(do
                                                                       (modal/piilota!)
                                                                       (toiminto-fn))
                                              {:luokka "pull-left"}]
                                    :poista [napit/poista hyvaksy #(do
                                                                     (modal/piilota!)
                                                                     (toiminto-fn))
                                             {:luokka "pull-left"}]
                                    :peruuta [napit/peruuta (or peruuta-txt "Peruuta") #(modal/piilota!)
                                              {:luokka "pull-right"}]
                                    :takaisin [napit/takaisin "Peruuta" #(modal/piilota!)
                                               {:luokka "pull-right"}]
                                    :tallenna [napit/yleinen-ensisijainen
                                               hyvaksy #(do
                                                          (modal/piilota!)
                                                          (toiminto-fn))
                                               {:ikoni (ikonit/harja-icon-action-save)
                                                :luokka "pull-left"}]
                                    :uusi [napit/uusi hyvaksy #(do
                                                                 (modal/piilota!)
                                                                 (toiminto-fn))
                                           {:luokka "pull-left"}]
                                    nil)
                                  {:key (str "varmistus-nappi-" tyyppi)})))]
                   :modal-luokka modal-luokka :content-tyyli content-tyyli :body-tyyli body-tyyli}
                  sisalto)))

(def modal-muut-vastaanottajat
  {:otsikko "Lisää muita vastaanottajien sähköpostiosoitteita pilkulla eroteltuna"
   :nimi :muut-vastaanottajat :tyyppi :email :palstoja 3
   :validoi [[:email]]})

(def modal-saateviesti {:otsikko "Vapaaehtoinen saateviesti, joka liitetään sähköpostiin"
                        :koko [90 8]
                        :nimi :saate :palstoja 3 :tyyppi :text})

(def modal-sahkopostikopio {:teksti "Lähetä sähköpostiini viestin kopio"
                            :nayta-rivina? true :palstoja 3
                            :nimi :kopio-itselle? :tyyppi :checkbox})
