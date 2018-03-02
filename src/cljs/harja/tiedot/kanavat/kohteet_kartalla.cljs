(ns harja.tiedot.kanavat.kohteet-kartalla
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as kokonaishintaiset]
            [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as lisatyot]
            [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as hairiotilanteet]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.kanavat.kohteenosa :as kohteenosa]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [clojure.set :as set]
            [clojure.string :as clj-str]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce karttataso-kohteet (atom false))

(defonce aktiivinen-nakyma
  (reaction
    (let [tila-kok @kokonaishintaiset/tila
          tila-lisa @lisatyot/tila
          tila-hairio @hairiotilanteet/tila
          kokonaishintaiset-nakymassa? (:nakymassa? tila-kok)
          lisatyot-nakymassa? (:nakymassa? tila-lisa)
          hairiotilanne-nakymassa? (:nakymassa? tila-hairio)
          tila (cond
                 kokonaishintaiset-nakymassa? {:avattu-tieto (-> tila-kok :avattu-toimenpide)
                                               :avattu-kohde (-> tila-kok :avattu-toimenpide ::kanavan-toimenpide/kohde)
                                               :gridissa-olevat-kohteen-tiedot (:toimenpiteet tila-kok)}
                 lisatyot-nakymassa? {:avattu-tieto (-> tila-lisa :avattu-toimenpide)
                                      :avattu-kohde (-> tila-lisa :avattu-toimenpide ::kanavan-toimenpide/kohde)
                                      :gridissa-olevat-kohteen-tiedot (:toimenpiteet tila-lisa)}
                 hairiotilanne-nakymassa? {:avattu-tieto (-> tila-hairio :valittu-hairiotilanne)
                                           :avattu-kohde (-> tila-hairio :valittu-hairiotilanne ::hairiotilanne/kohde)
                                           :gridissa-olevat-kohteen-tiedot (:hairiotilanteet tila-hairio)})]
      {:tila tila
       :nakyma (cond
                 kokonaishintaiset-nakymassa? :kokonaishintaiset
                 lisatyot-nakymassa? :lisatyot
                 hairiotilanne-nakymassa? :hairiotilanteet)})))

(defn tekstiksi [avain]
  (clj-str/capitalize (clj-str/replace (name avain) #"[_-]" " ")))

(defn- kohde-valittu? [kohde]
  (= (::kohde/id kohde) (-> @aktiivinen-nakyma :tila :avattu-kohde ::kohde/id)))

(defn- kohde-on-gridissa? [kohde gridissa-olevat-kohteen-tiedot nakyma]
  (let [kohteen-avain (case nakyma
                        (:kokonaishintaiset :lisatyot) ::kanavan-toimenpide/kohde
                        :hairiotilanteet ::hairiotilanne/kohde
                        nil)]
    (some #(= (::kohde/id kohde) (-> % kohteen-avain ::kohde/id))
          gridissa-olevat-kohteen-tiedot)))

(defn on-item-click-fn [klikattu-kohde]
  (let [klikatun-kohteen-id (::kohde/id klikattu-kohde)
        kohde (some (fn [kohde]
                      (when (= (::kohde/id kohde) klikatun-kohteen-id)
                        kohde))
                    @kanavaurakka/kanavakohteet)]
    (case (:nakyma @aktiivinen-nakyma)
      :kokonaishintaiset (swap! kokonaishintaiset/tila update
                                :avattu-toimenpide #(assoc % ::kanavan-toimenpide/kohde kohde))
      :lisatyot (swap! lisatyot/tila update
                       :avattu-toimenpide #(assoc % ::kanavan-toimenpide/kohde kohde))
      :hairiotilanteet (swap! hairiotilanteet/tila update
                              :valittu-hairiotilanne #(assoc % ::hairiotilanne/kohde kohde))
      nil)))

(defn- kohteelle-tiedot [kohde nakyma gridissa-olevat-kohteen-tiedot]
  (let [{:keys [tietokentta gridin-kohde-avain kohteen-tiedot-fn]}
        (case nakyma
          (:kokonaishintaiset :lisatyot) {:tietokentta :toimenpiteet
                                          :gridin-kohde-avain ::kanavan-toimenpide/kohde
                                          :kohteen-tiedot-fn #(identity
                                                                {:huoltokohde (-> % ::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/nimi)
                                                                 :kohteenosan-tyyppi (when-let [tyyppi (-> % ::kanavan-toimenpide/kohteenosa ::kohteenosa/tyyppi)]
                                                                                       (tekstiksi tyyppi))
                                                                 :kuittaaja (str (-> % ::kanavan-toimenpide/kuittaaja ::kayttaja/etunimi) " "
                                                                                 (-> % ::kanavan-toimenpide/kuittaaja ::kayttaja/sukunimi))
                                                                 :lisatieto (::kanavan-toimenpide/lisatieto %)
                                                                 :muu-toimenpide (::kanavan-toimenpide/muu-toimenpide %)
                                                                 :pvm (when-let [paivamaara (::kanavan-toimenpide/pvm %)] (pvm/pvm paivamaara))
                                                                 :suorittaja (::kanavan-toimenpide/suorittaja %)
                                                                 :toimenpide (-> % ::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi)})}
          :hairiotilanteet {:tietokentta :hairiot
                            :gridin-kohde-avain ::hairiotilanne/kohde
                            :kohteen-tiedot-fn #(identity
                                                  {:kohde (-> % ::hairiotilanne/kohde ::kohde/nimi)
                                                   :havaintoaika (when-let [paivamaara (::hairiotilanne/havaintoaika %)] (pvm/pvm paivamaara))
                                                   :korjauksen-tila (when-let [korjauksen-tila (::hairiotilanne/korjauksen-tila %)] (tekstiksi korjauksen-tila))
                                                   :vikaluokka (when-let [vikaluokka (::hairiotilanne/vikaluokka %)] (tekstiksi vikaluokka))
                                                   :korjaustoimenpide (::hairiotilanne/korjaustoimenpide %)
                                                   :kuittaaja (str (-> % ::hairiotilanne/kuittaaja ::kayttaja/etunimi) " "
                                                                   (-> % ::hairiotilanne/kuittaaja ::kayttaja/sukunimi))
                                                   :korjausaika-h (::hairiotilanne/korjausaika-h %)
                                                   :syy (::hairiotilanne/syy %)})}
          nil)
        kohteen-tiedot (keep #(when (= (-> % gridin-kohde-avain ::kohde/id) (::kohde/id kohde))
                                (kohteen-tiedot-fn %))
                             gridissa-olevat-kohteen-tiedot)]
    (if (empty? kohteen-tiedot)
      kohde
      (map #(assoc kohde tietokentta %)
           kohteen-tiedot))))

(defonce naytettavat-kanavakohteet
  (reaction
    (let [{:keys [gridissa-olevat-kohteen-tiedot avattu-tieto]} (:tila @aktiivinen-nakyma)
          ;; Yhdistetään kohteen ja kohteelle tehdyn toimenpiteen tiedot. Toimenpiteen tietoja näytetään kartan
          ;; infopaneelissa.
          kohteet (flatten (map (fn [kohde]
                                  (if avattu-tieto
                                    (assoc kohde
                                           :on-item-click on-item-click-fn
                                           :nayta-paneelissa? false
                                           :avaa-paneeli? false)
                                    (kohteelle-tiedot kohde (:nakyma @aktiivinen-nakyma) gridissa-olevat-kohteen-tiedot)))
                                @kanavaurakka/kanavakohteet))]
      (reduce (fn [kasitellyt kasiteltava]
                (cond
                  ;; Jos ollaan lomakkeella, näytetään kaikki kohteet
                  avattu-tieto (conj kasitellyt kasiteltava)
                  ;; Jos ollaan gridinäkymässä, niin näytetään vain ne kohteet, joille on tehty toimenpiteitä
                  (kohde-on-gridissa? kasiteltava gridissa-olevat-kohteen-tiedot (:nakyma @aktiivinen-nakyma)) (conj kasitellyt kasiteltava)
                  :else kasitellyt))
              [] kohteet))))

(defonce kohteet-kartalla
  (reaction
    (when @karttataso-kohteet
      (kartalla-esitettavaan-muotoon
        (map #(-> %
                  (set/rename-keys {::kohde/sijainti :sijainti})
                  (assoc :tyyppi-kartalla
                         (case (:nakyma @aktiivinen-nakyma)
                           (:kokonaishintaiset :lisatyot) :kohde-toimenpide
                           :hairiotilanteet :kohde-hairiotilanne
                           nil))
                  (dissoc ::kohde/kohteenosat ::kohde/kohdekokonaisuus ::kohde/urakat))
             @naytettavat-kanavakohteet)
        kohde-valittu?))))
