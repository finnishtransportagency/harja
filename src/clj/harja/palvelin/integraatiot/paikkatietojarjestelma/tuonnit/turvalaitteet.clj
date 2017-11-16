(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.kyselyt.vesivaylat.vatu-turvalaitteet :as q-turvalaitteet]))



;; TODO: geometrian / koordinaattien tallennus + haku?
;; onko the_geom ennen kuin tehdän mitään
(defn vie-turvalaitteet-kantaan [db turvalaite]
  (let [turvalaitenro (int (:numero turvalaite))
        nimi (:nimi turvalaite)
        sijainti (.toString (:the_geom turvalaite))        ;;(:koord turvalaite)
        ;;TODO: tallenna geometria oikeeseen sarakkeeseen (maarit)
        sijaintikuvaus (:sijainti turvalaite)
        tyyppi (:tyyppi turvalaite)
        tarkenne (:tarkenne turvalaite)
        tila (:tila turvalaite)
        vah_pvm (:vah_pvm turvalaite)
        toimintatila (:toim_tila turvalaite)
        rakenne (:rakenne turvalaite)
        navigointilaji (:nav_laji turvalaite)
        valaistu (:valaistu turvalaite)
        omistaja (:omistaja turvalaite)
        turvalaitenro_aiempi (:numero_v turvalaite)
        paavayla (:paavayla turvalaite)
        vaylat (:vaylat turvalaite)
        sql-parametrit {:turvalaitenro turvalaitenro
                        :nimi nimi
                        :sijainti sijainti
                        :sijaintikuvaus sijaintikuvaus
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
                        :vaylat vaylat}]

  ;;  harja.palvelin.ajastetut-tehtavat.turvalaitteiden-geometriat KATO TÄÄLTÄ MALLIA KOORDINAATTIEN KÄSITTELYYN

  (q-turvalaitteet/vie-turvalaitetauluun<! db sql-parametrit)))
