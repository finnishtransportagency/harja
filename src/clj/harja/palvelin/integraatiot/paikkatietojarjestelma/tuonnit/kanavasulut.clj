(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.kanavat.kanavasulut :as q-kanavasulut]))

(defn vie-kanavasulku-entry [db kanavasulku]
  (let [kanavanro (:numero kanavasulku)
        aluenro (:aluenro kanavasulku)
        nimi (:nimi kanavasulku)
        kanavatyyppi (:kanava_ty kanavasulku)
        aluetyyppi (:alue_ty kanavasulku)
        kiinnitys (:kiinnit kanavasulku)
        porttityyppi (:portti_ty kanavasulku)
        kayttotapa (:kaytto_ty kanavasulku)
        sulku_leveys (:sulku_lev kanavasulku)
        sulku_pituus (:sulku_pit kanavasulku)
        alus_leveys (:alus_lev kanavasulku)
        alus_pituus (:alus_pit kanavasulku)
        alus_syvyys (:alus_syv kanavasulku)
        alus_korkeus (:alus_kor kanavasulku)
        sulkumaara (:sulkuja kanavasulku)
        putouskorkeus_1 (:putousk_1 kanavasulku)
        putouskorkeus_2 (:putousk_2 kanavasulku)
        alakanavan_alavertaustaso (:ala_ver_1 kanavasulku)
        alakanavan_ylavertaustaso (:ala_ver_2 kanavasulku)
        ylakanavan_alavertaustaso (:yla_ver_1 kanavasulku)
        ylakanavan_ylavertaustaso (:yla_ver_2 kanavasulku)
        kynnys_1 (:kynnys_1 kanavasulku)
        kynnys_2 (:kynnys_2 kanavasulku)
        vesisto (:vesisto kanavasulku)
        kanavakokonaisuus (:kanavakok kanavasulku)
        kanava_pituus (:kanava_pit kanavasulku)
        kanava_leveys (:kanava_lev kanavasulku)
        lahtopaikka (:mista kanavasulku)
        kohdepaikka (:mihin kanavasulku)
        omistaja (:omistaja kanavasulku)
        geometria (.toString (:the_geom kanavasulku))
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
      (q-kanavasulut/luo-kanavasulku<! db sql-parametrit))))

(defn vie-kanavasulut-kantaan [db shapefile]
  (log/debug (str "vie-kanavasulut-kantaan TIEDOSTO: " shapefile))
  (if shapefile
    (do
      (log/debug (str "Tuodaan kanavasulut kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (doseq [kanavasulku (shapefile/tuo shapefile)]
                                  (vie-kanavasulku-entry db kanavasulku)))
      (log/debug "Kanavasulkujen tuonti kantaan valmis."))
    (log/debug "Kanavasulkujen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
