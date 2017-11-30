(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.vesivaylat.vatu-turvalaitteet :as q-turvalaitteet]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [clj-time.coerce :as c]))


(defn vie-turvalaite-entry [db turvalaite]
  (let [pvm_str (:vahv_pvm turvalaite)
        turvalaitenro (int (:tlnumero turvalaite))
        nimi (:nimi turvalaite)
        ;koordinaatit (:koord turvalaite)                      ;;sanomassa vastaanotetut koordinaatit, itse geometria tallennetaan geometria-sarakkeeseen
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
        turvalaitenro_aiempi (int (:vanhatlnro turvalaite))
        paavayla (:paavayla turvalaite)
        vaylat (when (:vaylat turvalaite) (konv/seq->array (map #(Integer. %) (str/split (:vaylat turvalaite) #","))))
        geometria (.toString (:the_geom turvalaite))
        sql-parametrit {:turvalaitenro turvalaitenro
                        :nimi nimi
                        ;:koordinaatit koordinaatit
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
                        :luoja "Integraatio"
                        :muokkaaja "Integraatio"}]

    (do
      (log/debug (str "Tallennetaan turvalaitenumero " turvalaitenro))
      (q-turvalaitteet/vie-turvalaitetauluun<! db sql-parametrit)
      )))


(defn vie-turvalaitteet-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan turvalaitteet kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (doseq [turvalaite (shapefile/tuo shapefile)]
                                  (vie-turvalaite-entry db turvalaite)))
      (log/debug "Turvalaitteiden tuonti kantaan valmis."))
    (log/debug "Turvalaitteiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))

