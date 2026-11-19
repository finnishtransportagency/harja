(ns harja.tyokalut.komponenttirekisteri
  "Tulostaa UI-komponenttirekisterin EDN:nä ilman appia, tietokantaa tai selainta.
  Käyttö: lein komponenttirekisteri [:id nappi] [:ryhma lomakkeet]"
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [harja.ui.komponentit.rekisteri :as rekisteri]))

(def ^:private tulostettavat-avaimet
  [:id :ryhma :rooli :suositus :korvaa :slotit :tilat :sallitut-vanhemmat :asetukset :bootstrap-tila :ankkuri])

(defn hae-kaikki []
  rekisteri/komponentit)

(defn parsi-argumentit
  "Muuntaa komentoriviargumentit [\":id\" \"nappi\" ...] suodattimiksi {:id :nappi ...}."
  [argumentit]
  (into {}
        (map (fn [[avain arvo]]
               [(keyword (str/replace avain #"^:" "")) (keyword arvo)]))
        (partition 2 argumentit)))

(defn- poista-funktiot [arvo]
  (walk/postwalk (fn [x] (if (fn? x) nil x)) arvo))

(defn- siisti [komponentti]
  (-> komponentti
      (assoc :ankkuri (rekisteri/ankkuri komponentti))
      (select-keys tulostettavat-avaimet)
      poista-funktiot))

(defn muotoile
  "Suodattaa komponentit annetuilla :id tai :ryhma -suodattimilla.
  Palauttaa {:tulos [...]} tai {:virhe \"...\"}."
  [{:keys [id ryhma]}]
  (let [komponentit (cond->> (hae-kaikki)
                      ryhma (filter #(= ryhma (:ryhma %)))
                      id (filter #(= id (:id %)))
                      :aina vec)]
    (if (and id (empty? komponentit))
      {:virhe (str "Tuntematon komponentti id:llä " id)}
      {:tulos (mapv siisti komponentit)})))

(defn -main [& argumentit]
  (let [{:keys [tulos virhe]} (muotoile (parsi-argumentit argumentit))]
    (if virhe
      (do (binding [*out* *err*] (println virhe))
          (System/exit 1))
      (println (pr-str tulos)))))
