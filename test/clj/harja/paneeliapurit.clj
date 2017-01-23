(ns harja.paneeliapurit
  (:require [harja.ui.kartta.infopaneelin-sisalto :as paneeli]))

(defn skeeman-luonti-onnistuu?
  ([tyyppi-kartalla data] (skeeman-luonti-onnistuu?
                            (assoc data :tyyppi-kartalla tyyppi-kartalla)))
  ([data]
   (if-not (:tyyppi-kartalla data)
     false

     (try
       (some?
         (-> data
             (paneeli/infopaneeli-skeema)
             (paneeli/skeema-ilman-tyhjia-riveja)
             (paneeli/validoi-infopaneeli-skeema true)))
       (catch Exception e
         (taoensso.timbre/debug e)
         false)))))

(defn skeeman-luonti-onnistuu-kaikille?
  ([tyyppi-kartalla coll]
   (skeeman-luonti-onnistuu-kaikille?
     (map #(assoc % :tyyppi-kartalla tyyppi-kartalla) coll)))
  ([coll]
   (when (not-empty coll) (every? true? (map skeeman-luonti-onnistuu? coll)))))