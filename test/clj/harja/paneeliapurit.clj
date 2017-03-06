(ns harja.paneeliapurit
  (:require [harja.ui.kartta.infopaneelin-sisalto :as paneeli]))

(def infopaneeli-skeema (var paneeli/infopaneeli-skeema))
(def skeema-ilman-tyhjia-riveja (var paneeli/skeema-ilman-tyhjia-riveja))
(def validoi-infopaneeli-skeema (var paneeli/validoi-infopaneeli-skeema))

(defn skeeman-luonti-onnistuu?
  ([tyyppi-kartalla data] (skeeman-luonti-onnistuu?
                            (assoc data :tyyppi-kartalla tyyppi-kartalla)))
  ([data]
   (if-not (:tyyppi-kartalla data)
     false

     (try
       (some?
         (-> data
             (infopaneeli-skeema)
             (skeema-ilman-tyhjia-riveja)
             (validoi-infopaneeli-skeema true)))
       (catch Exception e
         (taoensso.timbre/debug e)
         false)))))

(defn skeeman-luonti-onnistuu-kaikille?
  ([tyyppi-kartalla coll]
   (skeeman-luonti-onnistuu-kaikille?
     (map #(assoc % :tyyppi-kartalla tyyppi-kartalla) coll)))
  ([coll]
   (when-not (empty? coll) (every? skeeman-luonti-onnistuu? coll))))
