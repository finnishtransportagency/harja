(ns harja.palvelin.integraatiot.vayla-rest.sahkoposti.sanomat.sahkoposti-lahetys
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.string :as string])
  (:import (java.util UUID)))

(spec/def ::ei-tyhja-teksti (spec/and string? (complement string/blank?)))
(spec/def ::sahkopostiosoite (spec/and string? #(string/includes % "@")))
(spec/def ::base64-merkkijono #"[^-A-Za-z0-9+/=]|=[^=]|={3,}$")

(spec/def ::viestiId ::ei-tyhja-teksti)
(spec/def ::otsikko string?)
(spec/def ::sisalto string?)
(spec/def ::vastaanottajat (spec/coll-of ::sahkopostiosoite))
(spec/def ::liiteContentType #(partial = "application/pdf"))
(spec/def ::liiteNimi ::ei-tyhja-teksti)
(spec/def ::liiteData ::base64-merkkijono)

(defn kaikki-tai-ei-mitaan-liite-avaimista [m]
  ;; todo: käytä spec/alt oman funkkarin sijaan
  (if-let [ks (select-keys m [:liiteData :liiteNimi :liiteContentType])]
    (= 3 (count ks))
    true))

(spec/def ::sahkoposti-lahetys-sanoma (spec/and (spec/keys :req-un [::viestiId ::lahettaja ::otsikko ::sisalto ::vastaanottajat]
                                                           :opt-un [::liiteData ::liiteNimi ::liiteContentType])
                                                kaikki-tai-ei-mitaan-liite-avaimista))

