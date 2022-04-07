(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset
  (:require [tuck.core :refer [process-event] :as tuck]
            [reagent.core :as r]
            [harja.domain.paikkaus :as paikkaus]
            ;[harja.tiedot.urakka.paallystys :as paallystys]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.ui.kartta.ikonit :refer [sijainti-ikoni pinni-ikoni nuoli-ikoni]]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm])
  (:require-macros [reagent.ratom :as ratom]))

(defn- formatoi-pvm 
[_pvm]
  (when (some? _pvm)
    (pvm/fmt-p-k-v-lyhyt _pvm)))

(def karttataso-paikkausten-paallystysilmoitukset (r/atom []))
(defonce karttataso-nakyvissa? (r/atom false))

(defrecord FiltteriValitseVuosi [uusi-vuosi])
(defrecord FiltteriValitseTila [tila valittu?])

(defonce valitut-kohteet-atom (r/atom #{}))
(defonce paallystysilmoitukset-kartalla 
  (ratom/reaction 
   (let [;; Näytä vain valittu kohde kartalla
         valitut-kohteet (if (= (count @valitut-kohteet-atom) 0)
                           nil
                           @valitut-kohteet-atom)
         kohteet (keep (fn [kohde]
                         (when
                             (and (or (nil? valitut-kohteet)
                                      (contains? valitut-kohteet (:id kohde)))
                                  (:sijainti kohde))
                           kohde))
                       @karttataso-paikkausten-paallystysilmoitukset)
         tyomenetelmat (:tyomenetelmat @tila/paikkauskohteet)]
     (when (and (not-empty kohteet) @karttataso-nakyvissa?)
       (with-meta (mapv (fn [{:keys [aosa losa let aet tie] :as kohde}]
                          (when (:sijainti kohde)
                            {:alue (merge {:tyyppi-kartalla :paikkaukset-paikkausten-paallystysilmoitukset
                                           :stroke {:width 8
                                                    :color (asioiden-ulkoasu/tilan-vari (:tila kohde))}}
                                          (-> kohde :sijainti))
                             :tyyppi-kartalla :paikkaukset-paikkausten-paallystysilmoitukset
                             :selite {:teksti "POT-kohde"
                                      :img (pinni-ikoni "sininen")}
                             :infopaneelin-tiedot {:ulkoinen-id (:ulkoinen-id kohde)
                                                   :nimi (:nimi kohde)
                                                   :tila (:paikkauskohteen-tila kohde)
                                                   :pot-tila (:pot-tila kohde)
                                                   :pot-paatos (:pot-paatos kohde)
                                                   :menetelma (-> kohde :potin-tiedot :tyomenetelma)
                                                   :aikataulu (:formatoitu-aikataulu kohde)
                                                   :alkoi (formatoi-pvm (:pot-tyo-alkoi kohde))
                                                   :paattyi (formatoi-pvm (:pot-tyo-paattyi kohde))
                                                   :valmistui (formatoi-pvm (:pot-valmistumispvm kohde))
                                                   :takuuaika (str (:takuuaika kohde) " vuotta")
                                                   :tierekisteriosoite {:numero tie :alkuosa aosa :alkuetaisyys aet :loppuosa losa :loppuetaisyys let}
                                                   :alkupvm (formatoi-pvm (:alkupvm kohde))}
                             :ikonit [{:tyyppi :merkki
                                       :paikka [:loppu]
                                       :zindex 21
                                       :img (pinni-ikoni "sininen")}]}))
                        kohteet)
         {:selitteet [{:vari (map :color asioiden-ulkoasu/paikkaukset)
                       :teksti "Paikkausten POT-raportoitavat"}]})))))

(defn- paivita-valinta
  [valitut-tilat tila valittu?]
  (cond 
    ; valitaan kaikki
    (and valittu?
         (= (:nimi tila) :kaikki)) 
    #{:kaikki}
    
    ; poistetaan valinta
    (not valittu?)
    (disj valitut-tilat (:nimi tila))
    
    ; kaikki valittu, valitaan joku muu
    (and valittu?
         (contains? valitut-tilat :kaikki))
    (conj #{} (:nimi tila))
    ; joku muu valittu, poistetaan valinnat
    
    :else 
    (conj valitut-tilat (:nimi tila))))

(def ilmoitukset [{:id 2}])
(def kohteet [{:id 5 :ilm-id 2 :ilm? true} {:id 3}])


(defn paivita-karttatiedot [paallystysilmoitukset app]
  (let [{:keys [paikkauskohteet]} app
        ilmoitukset (reduce (fn [kaikki kohde]
                              (if (:pot? kohde) 
                                (conj kaikki (assoc kohde :potin-tiedot
                                                    (some #(when (= (:id %) (:pot-id kohde)) %) 
                                                          paallystysilmoitukset)))
                                kaikki)) 
                            [] paikkauskohteet)]
    (reset! karttataso-paikkausten-paallystysilmoitukset ilmoitukset)))

(extend-protocol tuck/Event

  FiltteriValitseVuosi
  (process-event [{uusi-vuosi :uusi-vuosi} app]
    (let [app (assoc-in app [:urakka-tila :valittu-urakan-vuosi] uusi-vuosi)]
      app))

  FiltteriValitseTila
  (process-event [{:keys [tila valittu?]} app]
    (update app :valitut-tilat paivita-valinta tila valittu?)))
