(ns harja.palvelin.palvelut.laadunseuranta.yhteiset
  (:require [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]))

(def laatupoikkeama-xf
  (comp
    (geo/muunna-pg-tulokset :sijainti)
    (map konv/alaviiva->rakenne)
    (map #(assoc % :selvitys-pyydetty (:selvityspyydetty %)))
    (map #(dissoc % :selvityspyydetty))
    (map #(assoc % :tekija (keyword (:tekija %))))
    (map #(update-in % [:paatos :paatos]
                     (fn [p]
                       (when p (keyword p)))))
    (map #(update-in % [:paatos :kasittelytapa]
                     (fn [k]
                       (when k (keyword k)))))
    (map #(if (nil? (:kasittelyaika (:paatos %)))
            (dissoc % :paatos)
            %))))
