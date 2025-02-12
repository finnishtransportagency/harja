(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.kanavat.kanavasulut :as q-kanavasulut]))


(defn merkitse-kanavasulut-poistetuksi [db]
  (q-kanavasulut/merkitse-kanavasulut-poistetuksi<! db {:muokkaaja "Integraatio"}))

(defn vie-kanavasulku-entry
  "Aineistossa myös seuraavat arvot, mutta näillä ei juurikaan mitään tehdä:
   aluenro, kanavatyyppi, kynnys_1, kynnys_2
   alakanavan_alavertaustaso, alakanavan_ylavertaustaso
   ylakanavan_ylavertaustaso, ylakanavan_alavertaustaso"
  [db {:keys [numero nimi kanavaalue kiinnittym porttiseli kayttoseli sulkuleve0 sulkulevey
              alusleveys aluspituus alussyvyys aluskorkeu id sulkuja putouskork putouskor0 vesisto
              kanavapitu kanavaleve mista mihin omistaja the_geom]} :as kanavasulku]
  (let [geometria (.toString (:the_geom kanavasulku))
        sql-parametrit {:kanavanro kanavanro
                        :aluenro aluenro
                        :nimi nimi
                        :kanavatyyppi kanavatyyppi
                        :aluetyyppi aluetyyppi
                        :kiinnitys kiinnitys
                        :porttityyppi porttityyppi
                        :kayttotapa kayttotapa
                        :sulku_leveys sulku_leveys
                        :sulku_pituus sulku_pituus
                        :alus_leveys alus_leveys
                        :alus_pituus alus_pituus
                        :alus_syvyys alus_syvyys
                        :alus_korkeus alus_korkeus
                        :sulkumaara sulkumaara
                        :putouskorkeus_1 putouskorkeus_1
                        :putouskorkeus_2 putouskorkeus_2
                        :alakanavan_alavertaustaso alakanavan_alavertaustaso
                        :alakanavan_ylavertaustaso alakanavan_ylavertaustaso
                        :ylakanavan_alavertaustaso ylakanavan_alavertaustaso
                        :ylakanavan_ylavertaustaso ylakanavan_ylavertaustaso
                        :kynnys_1 kynnys_1
                        :kynnys_2 kynnys_2
                        :vesisto vesisto
                        :kanavakokonaisuus kanavakokonaisuus
                        :kanava_pituus kanava_pituus
                        :kanava_leveys kanava_leveys
                        :lahtopaikka lahtopaikka
                        :kohdepaikka kohdepaikka
                        :omistaja omistaja
                        :geometria geometria
                        :luoja "Integraatio"
                        :muokkaaja "Integraatio"
                        :poistettu false}]
    (do
      (q-kanavasulut/luo-kanavasulku<! db sql-parametrit))))

(defn vie-kanavasulut-kantaan [db shapefile]
  (if shapefile
    (do
      ; (log/debug (str "Tuodaan kanavasulut kantaan tiedostosta " shapefile))
      (println "\n Ajetaan sulut.. " shapefile)
      (jdbc/with-db-transaction [db db]
        (merkitse-kanavasulut-poistetuksi db) ;; poistetut kanavat ovat poistuneet aineistosta
        (doseq [kanavasulku (shapefile/tuo shapefile)]
          (dorun
            (println "\n sulku: " kanavasulku)
            #_(vie-kanavasulku-entry db kanavasulku)
            (doseq [x kanavasulku]
              (println "x: " (first x) " - " (second x))))))
      (log/debug "Kanavasulkujen tuonti kantaan valmis."))
    (log/debug "Kanavasulkujen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
