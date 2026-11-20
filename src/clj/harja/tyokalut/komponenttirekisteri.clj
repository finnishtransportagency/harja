(ns harja.tyokalut.komponenttirekisteri
  "Tulostaa UI-komponenttirekisterin EDN:nä ilman appia, tietokantaa tai selainta.
  Käyttö: lein komponenttirekisteri [:id nappi] [:ryhma lomakkeet]"
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [harja.ui.perus.rekisteri :as rekisteri]))

(def ^:private sallitut-suodattimet #{:id :ryhma})

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
      poista-funktiot))

(defn tarkista-argumentit
  "Palauttaa virheilmoituksen, jos argumentit eivät ole avain-arvo-pareja sallituille suodattimille."
  [argumentit]
  (cond
    (odd? (count argumentit))
    "Argumentit annetaan avain-arvo-pareina, esim. :id nappi"

    :else
    (when-let [tuntemattomat (seq (remove sallitut-suodattimet
                                          (keys (parsi-argumentit argumentit))))]
      (str "Tuntematon suodatin: " (str/join ", " tuntemattomat)
           ". Sallitut: " (str/join ", " (sort sallitut-suodattimet))))))

(defn muotoile
  "Suodattaa komponentit annetuilla :id tai :ryhma -suodattimilla.
  Palauttaa {:tulos [...]} tai {:virhe \"...\"}."
  [{:keys [id ryhma]}]
  (let [komponentit (cond->> (hae-kaikki)
                      ryhma (filter #(= ryhma (:ryhma %)))
                      id (filter #(= id (:id %)))
                      :aina vec)]
    (cond
      (and id (empty? komponentit)) {:virhe (str "Tuntematon komponentti id:llä " id)}
      (and ryhma (empty? komponentit)) {:virhe (str "Ryhmästä " ryhma " ei löytynyt komponentteja")}
      :else {:tulos (mapv siisti komponentit)})))

(defn -main [& argumentit]
  (let [{:keys [tulos virhe]} (if-let [virhe (tarkista-argumentit argumentit)]
                                {:virhe virhe}
                                (muotoile (parsi-argumentit argumentit)))]
    (if virhe
      (do (binding [*out* *err*] (println virhe))
          (System/exit 1))
      (println (pr-str tulos)))))
