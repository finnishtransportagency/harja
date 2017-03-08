(ns harja.palvelin.integraatiot.api.kasittely.tiemerkintatoteumat
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.kyselyt.yllapito-muut-toteumat :as yllapitototeuma-q]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.id :as id]
            [harja.domain.tiemerkinta-toteumat :as d]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn luo-tai-paivita-tiemerkintatoteumat [db kayttaja urakka-id yllapitokohde-id tiemerkintatoteumat]
  (doseq [{{:keys [tunniste hinta muutospvm selite tienumero pituus yllapitoluokka hintatyyppi] :as toteuma}
           :tiemerkintatoteuma}
          tiemerkintatoteumat]
    (let [ulkoinen-id (:id tunniste)
          luoja-id (:id kayttaja)
          id (yllapitototeuma-q/hae-tiemerkintatoteuman-id-ulkoisella-idlla db luoja-id ulkoinen-id)
          yllapitokohde (when yllapitokohde-id (yllapitokohteet-q/hae-yllapitokohde db yllapitokohde-id))
          hinta-kohteelle (when yllapitokohde-id (d/maarittele-hinnan-kohde yllapitokohde))
          muutospvm (json/aika-string->java-sql-date muutospvm)
          sql-parametrit {:yllapitokohde yllapitokohde
                          :hinta hinta
                          :hintatyyppi hintatyyppi
                          :muutospvm muutospvm
                          :hinta_kohteelle (when yllapitokohde-id hinta-kohteelle)
                          :selite selite
                          :tr_numero (when-not yllapitokohde-id tienumero)
                          :yllapitoluokka (when-not yllapitokohde-id yllapitoluokka)
                          :pituus (when-not yllapitokohde-id pituus)
                          :ulkoinen_id ulkoinen-id
                          :luoja luoja-id}]
      (if (id/id-olemassa? id)
        (yllapitototeuma-q/paivita-tiemerkintaurakan-yksikkohintainen-tyo<!
          db (merge sql-parametrit {:id id :urakka urakka-id :poistettu false}))
        (yllapitototeuma-q/luo-tiemerkintaurakan-yksikkohintainen-tyo<! db (merge sql-parametrit {:urakka urakka-id}))))))

(defn poista-tiemerkintatoteumat
  ([db kayttaja urakka-id tiemerkintatoteumat] (poista-tiemerkintatoteumat db kayttaja urakka-id nil tiemerkintatoteumat))
  ([db kayttaja urakka-id yllapitokohde-id tiemerkintatoteumat]
   (when (and (not (nil? tiemerkintatoteumat)) (not (empty? tiemerkintatoteumat)))
     (let [sql-parametrit {:luoja_id (:id kayttaja)
                           :ulkoiset_idt (harja.kyselyt.konversio/seq->array tiemerkintatoteumat)
                           :urakka_id urakka-id
                           :yllapitokohde_id yllapitokohde-id}]
       (yllapitototeuma-q/poista-tiemerkintatoteumat-ulkoisilla-idlla! db sql-parametrit)))))