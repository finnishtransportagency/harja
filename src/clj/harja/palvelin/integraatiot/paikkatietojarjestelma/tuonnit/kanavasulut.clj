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
  (let [geometria (.toString the_geom)
        sql-parametrit {:kanavanro numero
                        :aluenro id
                        :nimi nimi
                        ; :kanavatyyppi kanavatyyppi
                        ;; Tyyppiä ei enää ole, taitaa olla sama kun aluetyyppi, eli esim "Sulku"
                        ;; Tätäkään arvoa, ei Harjassa käytetä, ja poistettu kannasta
                        :aluetyyppi kanavaalue
                        :kiinnitys kiinnittym
                        :porttityyppi porttiseli
                        :kayttotapa kayttoseli
                        :sulku_leveys sulkuleve0
                        :sulku_pituus sulkulevey ;; Jännästi nimettynä, mutta pitää paikkansa
                        :alus_leveys alusleveys
                        :alus_pituus aluspituus
                        :alus_syvyys alussyvyys
                        :alus_korkeus aluskorkeu
                        :sulkumaara sulkuja
                        :putouskorkeus_1 putouskork
                        :putouskorkeus_2 putouskor0
                        :vesisto vesisto
                        ; :kanavakokonaisuus kanavakokonaisuus
                        ; Kokonaisuutta ei myöskään uudessa aineistossa enää ole 
                        :kanava_pituus kanavapitu
                        :kanava_leveys kanavaleve
                        :lahtopaikka mista
                        :kohdepaikka mihin
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
