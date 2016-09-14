(ns harja.palvelin.ajastetut-tehtavat.laskytyshteenvedot
  "Ajastettu tehtävä laskutusyhteenvetojen muodostamiseksi valmiiksi välimuistiin"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.laskutusyhteenveto :as q]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]))

(defn muodosta-laskutusyhteenveto [db urakka]
  (log/info "muodostellaan...."))

(defn muodosta-laskutusyhteenvedot [db]
  (doseq [{:keys [id nimi]} (q/hae-urakat-joille-laskutusyhteenveto-ajetaan db)]
    (log/info "Muodostetaan laskutusyhteenveto valmiiksi urakalle: " nimi)
    (lukot/aja-lukon-kanssa db (str "laskutusyhteenveto:" id)
                            #(muodosta-laskutusyhteenveto db id))))
