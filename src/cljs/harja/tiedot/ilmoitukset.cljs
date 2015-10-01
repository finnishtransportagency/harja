(ns harja.tiedot.ilmoitukset
  (:require [reagent.core :refer [atom]]

            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.geo :as geo])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; FILTTERIT
(defonce ilmoitusnakymassa? (atom false))
(defonce valittu-ilmoitus (atom nil))

(def +ilmoitustyypit+ #{:kysely :toimenpidepyynto :tiedoitus})
(def +ilmoitustilat+ #{:suljetut :avoimet})

(defonce valinnat (reaction {:hallintayksikko (:id @nav/valittu-hallintayksikko)
                             :urakka (:id @nav/valittu-urakka)
                             :hoitokausi @u/valittu-hoitokausi
                             :aikavali (or @u/valittu-hoitokausi [nil nil])
                             :tyypit +ilmoitustyypit+
                             :tilat +ilmoitustilat+
                             :hakuehto ""}))

(defonce ilmoitushaku (atom 0))

(defn hae-ilmoitukset []
  (go (swap! ilmoitushaku inc)))

(defn jarjesta-ilmoitukset [tulos]
  (sort-by
    :ilmoitettu
    pvm/ennen?
    (mapv
      (fn [ilmo]
        (assoc ilmo :kuittaukset
                    (sort-by :kuitattu pvm/ennen? (:kuittaukset ilmo))))
      tulos)))

(defonce haetut-ilmoitukset
  (reaction<! [valinnat @valinnat
               haku @ilmoitushaku]
              {:odota 100}
              (go
                (if (zero? haku)
                  []
                  (let [tulos (<! (k/post! :hae-ilmoitukset
                                           (-> valinnat
                                               ;; jos tyyppiä/tilaa ei valittu, ota kaikki
                                               (update-in [:tyypit]
                                                          #(if (empty? %) +ilmoitustyypit+ %))
                                               (update-in [:tilat]
                                                          #(if (empty? %) +ilmoitustilat+ %)))))]
                    (when-not (k/virhe? tulos)
                      (jarjesta-ilmoitukset tulos)))))))

;; POLLAUS
(def pollaus-id (atom nil))
(def +intervalli+ 60000) ;60000ms -> 1min

(def ilmoitus-kartalla-xf
  #(assoc %
    :type :ilmoitus
    :alue {:type        :circle
           :coordinates (geo/ikonin-sijainti (:sijainti %))
           :color        (if (= (:id %) (:id @valittu-ilmoitus)) "green" "blue")
           :radius      5000
           :stroke      {:color "black" :width 10}}))

(defonce ilmoitusta-klikattu
         (tapahtumat/kuuntele! :ilmoitus-klikattu
                               (fn [ilmoitus]
                                 (reset! valittu-ilmoitus (dissoc ilmoitus :type :alue)))))

(defonce karttataso-ilmoitukset (atom false))

(defonce ilmoitukset-kartalla
         (reaction
           @valittu-ilmoitus
           (when @karttataso-ilmoitukset
             (into [] (map ilmoitus-kartalla-xf) @haetut-ilmoitukset))))


(defn lopeta-pollaus []
  (when-let [lopeta @pollaus-id]
    (lopeta)))

(defn aloita-pollaus
  []
  (lopeta-pollaus)
  (reset! pollaus-id (paivita-periodisesti haetut-ilmoitukset +intervalli+)))
