(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.kanavat.kanavat :as q-kanavat]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [clj-time.coerce :as c]))


;;TODO: uudelleennimeä epäselvästi nimetyt tiedot ALA_VER_1

(defn vie-kanava-entry [db kanava]
  (let [kynnys_1_str (:kynnys_1 kanava)
        kynnys_2_str (:kynnys_2 kanava)
        kanavanro (:numero kanava)
        aluenro (:aluenumero kanava)
        nimi (:nimi kanava)
        kanavatyyppi (:kanava_ty kanava)
        aluetyyppi (:alue_ty kanava)
        kiinnitys (:kiinnit kanava)
        porttityyppi (:portti_ty kanava)
        kayttotapa (:kaytto_ty kanava)
        sulku_leveys (:sulku_lev kanava)
        sulku_pituus (:sulku_pit kanava)
        alus_leveys (:alus_lev kanava)
        alus_pituus (:alus_pit kanava)
        alus_syvyys (:alus_syv kanava)
        alus_korkeus (:alus_kor kanava)
        sulkumaara (:sulkuja kanava)
        putouskorkeus_1 (:putousk_1 kanava)
        putouskorkeus_2 (:putousk_2 kanava)
        ALA_VER_1 (:ala_ver1 kanava)
        ALA_VER_2 (:ala_ver2 kanava)
        YLA_VER_1 (:yla_ver1 kanava)
        YLA_VER_2 (:yla_ver2 kanava)
        kynnys_1 ((if (not= kynnys_1_str "") #(Double/parseDouble (str/replace kynnys_1_str "," "."))))
        kynnys_2 ((if (not= kynnys_2_str "") #(Double/parseDouble (str/replace kynnys_2_str "," "."))))
        vesisto (:vesisto kanava)
        kanavakokonaisuus (:kanavakok kanava)
        kanava_pituus (:kanava_pit kanava)
        kanava_leveys (:kanava_lev kanava)
        lahtopaikka (:mista kanava)
        kohdepaikka (:mihin kanava)
        omistaja (:omistaja kanava)
        geometria (.toString (:the_geom kanava))
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
                        :ALA_VER_1 ALA_VER_1
                        :ALA_VER_2 ALA_VER_2
                        :YLA_VER_1 YLA_VER_1
                        :YLA_VER_2 YLA_VER_2
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
                        :muokkaaja "Integraatio"}]

    (do
      (println "VIEDÄÄN KANAVA TAULUUN!")
      (q-kanavat/vie-kanavatauluun<! db sql-parametrit)
      )))


(defn vie-kanavat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan kanavaa kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (doseq [kanava (shapefile/tuo shapefile)]
                                  (println (str "LÄHDETÄÄN VIEMÄÄN KANAVA TAULUUN!" kanava))
                                  (vie-kanava-entry db kanava)))
      (log/debug "Kanavien tuonti kantaan valmis."))
    (log/debug "Kanavien tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
