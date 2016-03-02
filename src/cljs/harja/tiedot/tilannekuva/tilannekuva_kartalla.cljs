(ns harja.tiedot.tilannekuva.tilannekuva-kartalla
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.atom :refer-macros [reaction<!]
             :refer [paivita-periodisesti]]
            [harja.ui.kartta.esitettavat-asiat
             :as esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon]]
            [harja.ui.openlayers :as openlayers]
            [clojure.string :as str])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tilannekuva (atom false))
(defonce haetut-asiat (atom nil))

(defonce tilannekuvan-asiat-kartalla (atom {}))

(def lisaa-karttatyyppi-fn
  {:ilmoitukset            #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))
   :turvallisuuspoikkeamat #(assoc % :tyyppi-kartalla :turvallisuuspoikkeama)
   :tarkastukset           #(assoc % :tyyppi-kartalla :tarkastus)
   :laatupoikkeamat        #(assoc % :tyyppi-kartalla :laatupoikkeama)
   :paikkaus               #(assoc % :tyyppi-kartalla :paikkaus)
   :paallystys             #(assoc % :tyyppi-kartalla :paallystys)

   ;; Tyokoneet on mäp, id -> työkone
   :tyokoneet              (fn [[_ tyokone]]
                             (assoc tyokone :tyyppi-kartalla :tyokone))

   :toteumat               #(assoc % :tyyppi-kartalla :toteuma)})

(def ^{:doc "Mäpätään tilannekuvan tasojen nimet :tilannekuva- etuliitteelle,
etteivät ne mene päällekkäin muiden tasojen kanssa."}
  karttatason-nimi
  {:ilmoitukset            :tilannekuva-ilmoitukset
   :turvallisuuspoikkeamat :tilannekuva-turvallisuuspoikkeamat
   :tarkastukset           :tilannekuva-tarkastukset
   :laatupoikkeamat        :tilannekuva-laatupoikkeamat
   :paikkaus               :tilannekuva-paikkaus
   :paallystys             :tilannekuva-paallystys
   :tyokoneet              :tilannekuva-tyokoneet
   :toteumat               :tilannekuva-toteumat})

(defmulti muodosta-karttataso (fn [taso uudet-asiat] taso))

(defmethod muodosta-karttataso :default [taso uudet-asiat]
  (kartalla-esitettavaan-muotoon
   uudet-asiat
   nil nil
   (map (lisaa-karttatyyppi-fn taso))))

(defn- toimenpiteen-selite [{:keys [toimenpide toimenpidekoodi]}]
  (let [[viivat _] (esitettavat-asiat/tehtavan-viivat-ja-nuolitiedosto
                    [toimenpide] false)]
    {:nimi toimenpide :teksti toimenpide
     :vari (esitettavat-asiat/viivojen-varit-leveimmasta-kapeimpaan viivat)}))

(defmethod muodosta-karttataso :toteumat [taso toimenpiteet]
  (log "toteumat taso tehdään!" (pr-str toimenpiteet))
  (openlayers/luo-kuvataso
   :tilannekuva
   (into #{}
         (map toimenpiteen-selite)
         toimenpiteet)))

;; Päivittää tilannekuvan karttatasot kun niiden tiedot ovat muuttuneet.
;; Muuntaa kartalla esitettävään muotoon ne tasot, joiden tiedot on oikeasti
;; muuttuneet.
(defn paivita-tilannekuvatasot
  "Päivittää tilannekuvan karttatasot kun niiden tiedot haetuissa asioissa
ovat muuttuneet. Ottaa sisään haettujen asioiden vanhan ja uuden version."
  [vanha uusi]
  (if (nil? uusi)
    ;; Jos tilannekuva poistuu näkyvistä, haetut-asiat on nil
    (reset! tilannekuvan-asiat-kartalla {})

    ;; Päivitä kaikki eri tyyppiset asiat
    (let [tasot (into #{} (concat (keys uusi) (keys vanha)))]
      (loop [uudet-tasot {}
             [taso & tasot] (seq tasot)]
        (if-not taso
          (swap! tilannekuvan-asiat-kartalla merge uudet-tasot)
          (let [vanhat-asiat (get vanha taso)
                uudet-asiat (get uusi taso)
                tason-nimi (karttatason-nimi taso)]
            (recur (cond
                     ;; Jos taso on nyt tyhjä, poistetaan se
                     ;; (nil taso poistuu kartalta)
                     (empty? uudet-asiat)
                     (assoc uudet-tasot tason-nimi nil)

                     ;; Jos tason asiat ovat muuttuneet, muodostetaan
                     ;; kartalla esitettävä muoto
                     (not= vanhat-asiat uudet-asiat)
                     (assoc uudet-tasot
                            tason-nimi (muodosta-karttataso taso uudet-asiat))

                     :default
                     uudet-tasot)
                   tasot)))))))

(add-watch haetut-asiat :paivita-tilannekuvatasot
           (fn [_ _ vanha uusi] (paivita-tilannekuvatasot vanha uusi)))
