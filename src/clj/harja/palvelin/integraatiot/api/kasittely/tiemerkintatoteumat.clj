(ns harja.palvelin.integraatiot.api.kasittely.tiemerkintatoteumat
  "Sisältää käsittelylogiikan tiemerkintätoteumien luomiseen, päivittämiseen ja poistamiseen Harja API:n kautta.
   Käytetään käsiteltäessä urakan tai urakan ylläpitokohteen tiemerkintätoteumia."
  
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.kyselyt.yllapito-muut-toteumat :as yllapitototeuma-q]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.id :as id]
            [harja.domain.tiemerkinta-toteumat :as d]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.kyselyt.konversio :as konversio]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn luo-tai-paivita-tiemerkintatoteumat
  ([db kayttaja urakka-id tiemerkintatoteumat]
   (luo-tai-paivita-tiemerkintatoteumat db kayttaja urakka-id nil tiemerkintatoteumat))
  ([db kayttaja urakka-id yllapitokohde-id tiemerkintatoteumat]
   (jdbc/with-db-transaction [db db]
     (doseq [{{:keys [tunniste hinta paivamaara selite tienumero pituus yllapitoluokka hintatyyppi] :as toteuma}
              :tiemerkintatoteuma}
             tiemerkintatoteumat]
       (let [ulkoinen-id (:id tunniste)
             luoja-id (:id kayttaja)
             id (yllapitototeuma-q/hae-tiemerkintatoteuman-id-ulkoisella-idlla db luoja-id ulkoinen-id)
             yllapitokohde (when yllapitokohde-id (first (yllapitokohteet-q/hae-yllapitokohde db {:id yllapitokohde-id})))
             hinta-kohteelle (when yllapitokohde-id (d/maarittele-hinnan-kohde yllapitokohde))
             paivamaara (json/aika-string->java-sql-date paivamaara)
             sql-parametrit {:yllapitokohde yllapitokohde-id
                             :hinta hinta
                             :hintatyyppi hintatyyppi
                             :paivamaara paivamaara
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
           (yllapitototeuma-q/luo-tiemerkintaurakan-yksikkohintainen-tyo<! db (merge sql-parametrit {:urakka urakka-id}))))))))

(defn poista-tiemerkintatoteumat
  ([db kayttaja urakka-id tiemerkintatoteumat] (poista-tiemerkintatoteumat db kayttaja urakka-id nil tiemerkintatoteumat))
  ([db kayttaja urakka-id yllapitokohde-id tiemerkintatoteumat]
   (when (and (not (nil? tiemerkintatoteumat)) (not (empty? tiemerkintatoteumat)))
     (let [sql-parametrit {:luoja_id (:id kayttaja)
                           :ulkoiset_idt (konversio/seq->array tiemerkintatoteumat)
                           :urakka_id urakka-id
                           :yllapitokohde_id yllapitokohde-id}]
       (yllapitototeuma-q/poista-tiemerkintatoteumat-ulkoisilla-idlla! db sql-parametrit)))))
