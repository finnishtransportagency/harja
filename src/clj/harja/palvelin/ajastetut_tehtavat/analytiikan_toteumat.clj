(ns harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat
  "Ajastettu tehtävä toteumien siirtämiseksi analytiikan tarpeeksi analytiikan-toteumat tauluun"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.toteumat :as toteuma-kyselyt]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.ajastetut-tehtavat-kyselyt :as ajastetut-tehtavat-kyselyt]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konversio]
            [harja.pvm :as pvm]
            [clojure.java.jdbc :as jdbc]))

(defn yrita-siirtaa-toteumat
  "Yrittää siirtää toteumat transaktion sisällä. Palauttaa true jos onnistui, false jos epäonnistui.
  Logittaa onnistumisen ajastetut_tehtavat tauluun. Epäonnistumisesta ei logiteta tässä, vaan kutsuvassa funktiossa."
  [db alkuaika-sql loppuaika-sql]
  (jdbc/with-db-transaction [db db]
    (toteuma-kyselyt/siirra-toteumat-analytiikalle db {:alkuaika alkuaika-sql :loppuaika loppuaika-sql})
    (ajastetut-tehtavat-kyselyt/lisaa_ajastettu_tehtava! db {:tyyppi "siirra_toteumat_analytiikalle"
                                                             :alkuaika_valilta alkuaika-sql
                                                             :loppuaika_valilta loppuaika-sql
                                                             :onnistunut true
                                                             :virhe nil})
    true))

(defn siirra-toteumat
  "Toteumat siirretään aina myöhään yöllä, jotta edellisen päivän kaikki toteumat ehtivät muodostua.
  Funktio olettaa, että sitä ajetaan ajastetusti yöllä, joten käytetän - nyt - hetkeä defaulttina.
  Siirretään kaikki ne toteumat, jotka on muodostuneet (lisätty) tai päivitetty edellisen ajokerran jälkeen.
  Edellinen ajokerta saadaan ajastetut_tehtavat taulusta."
  [db]
  (let [viimeisin-ajokerta (:loppuaika_valilta (first (ajastetut-tehtavat-kyselyt/hae-viimeisin-onnistunut-ajokerta db "siirra_toteumat_analytiikalle")))
        _ (log/info "Viimeisin onnistunut ajokerta analytiikan_toteumat siirrossa:" viimeisin-ajokerta)

        ;; Jotta ei varmasti menetetä yhtään muokattua tai lisättyä toteumaa, niin otetaan viimeisin ajokerta mukaan isommalla pensselillä, eli poistetaan siitä vielä 3h.
        viimeisin-ajokerta (when viimeisin-ajokerta (pvm/ajan-muokkaus viimeisin-ajokerta false 3 :tunti))
        alkuaika (or viimeisin-ajokerta
                   (pvm/ajan-muokkaus (pvm/joda-timeksi (pvm/nyt)) false 2 :paiva))
        alkuaika-sql (if (instance? java.sql.Timestamp alkuaika) alkuaika (konversio/joda-datetime->sql-timestamp alkuaika))
        loppuaika-sql (konversio/joda-datetime->sql-timestamp (pvm/joda-timeksi (pvm/nyt)))]
    (try
      (yrita-siirtaa-toteumat db alkuaika-sql loppuaika-sql)
      (log/info "Toteumien siirto analytiikan_toteumat tauluun onnistui aikaväliltä:" alkuaika-sql "-" loppuaika-sql)
      (catch Exception e
        (log/error e (str "Toteumien siirto analytiikan_toteumat tauluun epäonnistui aikaväliltä: "
                       alkuaika-sql " - " loppuaika-sql ". Virhe: " (.getMessage e)))
        (ajastetut-tehtavat-kyselyt/lisaa_ajastettu_tehtava! db {:tyyppi "siirra_toteumat_analytiikalle"
                                                                 :alkuaika_valilta alkuaika-sql
                                                                 :loppuaika_valilta loppuaika-sql
                                                                 :onnistunut false
                                                                 :virhe (str e)})))))

(defn- ajasta [db]
  (log/info "Ajastetaan toteumien siirto analytiikan_toteumat tauluun joka päivä.")
  (ajastettu-tehtava/ajasta-paivittain [5 15 0]
    (fn [_]
      (lukot/yrita-ajaa-lukon-kanssa
        db
        "analytiikan_toteumat_siirto"
        #(do
           (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Alkaa " (pvm/nyt))
           (siirra-toteumat db)
           (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Loppuu " (pvm/nyt)))))))

(defrecord AnalytiikanToteumat []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :analytiikan-toteumien-ajastus
      (ajasta db)))
  (stop [{poista :analytiikan-toteumien-ajastus :as this}]
    (poista)
    (dissoc this :analytiikan-toteumien-ajastus)))
