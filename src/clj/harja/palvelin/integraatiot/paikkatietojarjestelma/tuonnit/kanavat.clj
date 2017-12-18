(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.kanavat.kanavat :as q-kanavat]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [clj-time.coerce :as c]))


(defn vie-kanava-entry [db kanava]
  (let [kanavanro (:numero kanava)
        aluenro (:aluenro kanava)
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
        alakanavan_alavertaustaso (:ala_ver_1 kanava)
        alakanavan_ylavertaustaso (:ala_ver_2 kanava)
        ylakanavan_alavertaustaso (:yla_ver_1 kanava)
        ylakanavan_ylavertaustaso (:yla_ver_2 kanava)
        kynnys_1 (:kanava_1 kanava)
        kynnys_2 (:kanava_2 kanava)
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
                        :muokkaaja "Integraatio"}]

    (do
      (q-kanavat/vie-kanavatauluun<! db sql-parametrit)
      )))


(defn vie-kanavat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan kanavaa kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (doseq [kanava (shapefile/tuo shapefile)]
                                  (vie-kanava-entry db kanava)))
      (log/debug "Kanavien tuonti kantaan valmis."))
    (log/debug "Kanavien tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
