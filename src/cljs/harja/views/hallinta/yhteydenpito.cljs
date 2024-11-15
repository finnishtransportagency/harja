(ns harja.views.hallinta.yhteydenpito
  "Näkymästä voi lähettää kaikille käyttäjille sähköpostia. Hyödyllinen esimerkiksi päivityskatkoista tiedottamiseen."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.yhteydenpito :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.debug :as debug]
            [harja.loki :refer [log]]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]))


(def lomakkeen-tarkoitus-teksti
  "Tämä lomake lähettää sähköpostin niille Harja-käyttäjille, jotka ovat käyttäneet Harjaa viimeisen vuoden aikana. Omainaisuus on olemassa viestintään vakavissa häiriötilanteissa, kuten palvelunestohyökkäyksissä.")
(defn yhteydenttolomake [e! {yhteydenotto :yhteydenotto
                             lahetys-kaynnissa? :lahetys-kaynnissa?
                             :as app}]
  [:div.yhteydenpito
   [:h3 "Sähköpostin lähettäminen Harja-käyttäjille"]
   [:p lomakkeen-tarkoitus-teksti]
   [lomake/lomake
    {:ei-borderia? true
     :footer-fn (fn [yhteydenotto]
                (let [otsikko (get yhteydenotto :otsikko)
                      sisalto (get yhteydenotto :sisalto)
                      isValidOtsikko (not (clojure.string/blank? otsikko))
                      isValidSisalto (not (clojure.string/blank? sisalto))]
                  [napit/tallenna
                   "Lähetä"
                   #(if (and isValidOtsikko isValidSisalto)
                      (varmista-kayttajalta/varmista-kayttajalta
                        {:otsikko "Sähköposti kaikille Harja käyttäjille"
                         :sisalto [:div "Oletko varma, että haluat lähettää viestin kaikille vuoden sisällä kirjautuneille Harjan käyttäjille?"]
                         :hyvaksy "Lähetä"
                         :toiminto-fn (fn [] (e! (tiedot/->Laheta yhteydenotto)))
                         :disabled (true? lahetys-kaynnissa?)})
                      (do
                        (when (not isValidOtsikko)
                          (e! (assoc-in yhteydenotto [:errors :otsikko] "Otsikko on pakollinen.")))
                        (when (not isValidSisalto)
                          (e! (assoc-in yhteydenotto [:errors :sisalto] "Sisältö on pakollinen.")))
                        (e! "Otsikko ja sisältö ovat pakollisia!")))]))
     :muokkaa! #(e! (tiedot/->Muokkaa %))}
  [{:nimi :otsikko
    :otsikko "Otsikko"
    :tyyppi :string
    :palstoja 2
    :pakollinen? true
    :virhe-teksti (fn [yhteydenotto]
                    (get-in yhteydenotto [:errors :otsikko]))
    :luokka-fn (fn [yhteydenotto]
                 (if (get-in yhteydenotto [:errors :otsikko])
                   "kentta-virhe"
                   ""))}
   {:nimi :sisalto
    :otsikko "Sisältö"
    :tyyppi :text
    :koko [80 20]
    :pituus-max 2048
    :palstoja 2
    :pakollinen? true
    :virhe-teksti (fn [yhteydenotto]
                    (get-in yhteydenotto [:errors :sisalto]))
    :luokka-fn (fn [yhteydenotto]
                 (if (get-in yhteydenotto [:errors :sisalto])
                   "kentta-virhe"
                   ""))}]
  yhteydenotto]])

(defn yhteydenpito* []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn [e! app]
      (yhteydenttolomake e! app))))

(defn yhteydenpito []
  [tuck tiedot/data yhteydenpito*])


