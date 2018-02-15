(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.vesivaylat.turvalaitteet :as q-turvalaitteet]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [clj-time.coerce :as c]))

(defn vie-turvalaite-entry [db turvalaite]
  (let [pvm_str (:vahv_pvm turvalaite)
        turvalaitenro (int (:tlnumero turvalaite))
        nimi (:nimi turvalaite)
        sijainti (:sijainti turvalaite)
        tyyppi (:tyyppi turvalaite)
        tarkenne (:alatyyppi turvalaite)
        tila (:tila turvalaite)
        vah_pvm (if (> (count pvm_str) 0)(c/to-date (str (subs pvm_str 0 4) "-" (subs pvm_str 4 6) "-" (subs pvm_str 6 8)))) ;; päivämäärä saadaan avasta muodossa 19940411000000, voi olla tyhjä
        toimintatila (:toim_tila turvalaite)
        rakenne (:rak_tieto turvalaite)
        navigointilaji (:nav_laji turvalaite)
        valaistu (= "K" (:valaistu turvalaite))
        omistaja (:omistaja turvalaite)
        turvalaitenro_aiempi (when (:vanhatlnro turvalaite)
                               (int (:vanhatlnro turvalaite)))
        paavayla (:paavayla turvalaite)
        vaylat (when (:vaylat turvalaite) (konv/seq->array (map #(Integer. %) (str/split (:vaylat turvalaite) #","))))
        geometria (.toString (:the_geom turvalaite))
        sql-parametrit {:turvalaitenro (str turvalaitenro)
                        :nimi nimi
                        :sijainti sijainti
                        :tyyppi tyyppi
                        :tarkenne tarkenne
                        :tila tila
                        :vah_pvm vah_pvm
                        :toimintatila toimintatila
                        :rakenne rakenne
                        :navigointilaji navigointilaji
                        :valaistu valaistu
                        :omistaja omistaja
                        :turvalaitenro_aiempi turvalaitenro_aiempi
                        :paavayla paavayla
                        :vaylat vaylat
                        :geometria geometria
                        :luoja nil
                        :muokkaaja nil}] ;; onko joku sopiva käyttäjä joka voidaan kovakoodata?

    (q-turvalaitteet/vie-turvalaitetauluun<! db sql-parametrit)))

(defn vie-turvalaitteet-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan turvalaitteet kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (doseq [turvalaite (shapefile/tuo shapefile)]
                                  (vie-turvalaite-entry db turvalaite)))
      (log/debug "Turvalaitteiden tuonti kantaan valmis."))
    (log/debug "Turvalaitteiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
