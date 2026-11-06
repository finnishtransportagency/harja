(ns harja.domain.laadunseuranta.sanktion-summamaaritys
  "Sanktioprofiilin summamääritysten tulkinta: kiinteä automaattinen summa ja C-ryhmän
  manuaalinen viitesumma. Laskettavat summat ovat nimiavaruudessa sanktion-laskenta."
  (:require [clojure.string :as str]))

(defn- kiintea-automaattinen-summamaaritys?
  [{:keys [maaritystapa ohjeteksti]}]
  (and (= :automaattinen maaritystapa)
       (str/blank? ohjeteksti)))

(defn sanktiotyypilla-kiintea-automaattinen-summamaaritys?
  [{:keys [summamaaritykset]}]
  (boolean (some kiintea-automaattinen-summamaaritys? summamaaritykset)))

(defn sanktiotyypin-kiintea-automaattinen-summa
  [{:keys [summamaaritykset]}]
  (some (fn [{:keys [summa-euroina] :as summamaaritys}]
          (when (and (kiintea-automaattinen-summamaaritys? summamaaritys)
                     (number? summa-euroina)
                     (pos? summa-euroina)
                     #?(:clj (Double/isFinite (double summa-euroina))
                        :cljs (js/isFinite summa-euroina)))
            summa-euroina))
        summamaaritykset))

(defn kiintea-sanktiosumma
  [normaali-summa itse-ilmoitettu?]
  (if itse-ilmoitettu?
    (/ normaali-summa 2)
    normaali-summa))

(defn kiintea-summamalli
  [{:keys [voi-puolittaa-omailmoituksella] :as sanktiotyyppi} omailmoitettu?]
  (when-let [normaalimaara (sanktiotyypin-kiintea-automaattinen-summa sanktiotyyppi)]
    (let [omailmoitettu (boolean (and voi-puolittaa-omailmoituksella omailmoitettu?))]
      {:normaalimaara normaalimaara
       :omailmoitettu omailmoitettu
       :summa (kiintea-sanktiosumma normaalimaara omailmoitettu)})))

(defn c-ryhman-manuaalinen-maaritys
  "Palauttaa C-ryhmän sanktiotyypin manuaalisen viitesumman ja ohjetekstin tai nil.
  Viitesumma ei ole kiinteä: käyttäjä syöttää lopullisen summan itse."
  [laji sanktiotyyppi]
  (when (= :C laji)
    (some (fn [{:keys [maaritystapa summa-euroina ohjeteksti]}]
            (when (and (= :manuaalinen maaritystapa)
                       (some? summa-euroina)
                       (not (str/blank? ohjeteksti)))
              {:summa-euroina summa-euroina
               :ohjeteksti ohjeteksti}))
          (:summamaaritykset sanktiotyyppi))))
