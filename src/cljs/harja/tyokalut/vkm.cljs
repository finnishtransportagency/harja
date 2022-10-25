(ns harja.tyokalut.vkm
  "Viitekehysmuuntimen kyselyt (mm. TR-osoitehaku)"
  (:require [cljs.core.async :refer [<! >! chan put! close! alts! timeout]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(defn koordinaatti->trosoite-kahdella [[x1 y1] [x2 y2]]
  (k/post! :hae-tr-pisteilla {:x1 x1 :y1 y1 :x2 x2 :y2 y2} nil true))

(declare virhe?)

(defn tieosoite->viiva [trosoite]
  (k/post! :hae-tr-viivaksi trosoite nil true))

(defn tieosoite->piste [trosoite]
  (k/post! :hae-tr-viivaksi trosoite nil true))

(defn koordinaatti->trosoite [[x y]]
  (k/post! :hae-tr-pisteella {:x x :y y} nil true))

(defn virhe?
  "Tarkistaa epäonnistuiko VKM kutsu"
  [tulos]
  (harja.asiakas.kommunikaatio/virhe? tulos))

(def pisteelle-ei-loydy-tieta "Pisteelle ei löydy tietä.")
(def vihje-zoomaa-lahemmas "Yritä zoomata lähemmäs.")

(defn tieosien-pituudet
  ([tie] (tieosien-pituudet tie nil nil))
  ([tie aosa losa]
   (k/post! :hae-tr-osien-pituudet
            {:tie tie
             :aosa aosa
             :losa losa})))

(defn tieosan-ajoradat [tie osa]
  (k/post! :hae-tr-osan-ajoradat {:tie tie :osa osa}))

(defn tieosan-ajoratojen-geometriat [{:keys [numero alkuosa ajorata]}]
  (k/post! :hae-tr-osan-ajoratojen-geometriat {:tie numero :osa alkuosa :ajorata ajorata}))
