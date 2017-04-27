(ns harja.domain.urakka
  "Määrittelee urakka nimiavaruuden specit, jotta urakan tietoja voi käyttää namespacetuilla
  keywordeilla, esim. {:urakka/id 12}"
  (:require [clojure.spec :as s]
            [harja.domain.organisaatio :as o]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.domain.specql-db :refer [db]]
            #?@(:clj [[clojure.future :refer :all]
                      [specql.core :refer [define-tables]]]))
  #?(:cljs
     (:require-macros [specql.core :refer [define-tables]])))

(define-tables db
  ["urakka" ::urakka
   {"hanke_sampoid" ::hanke-sampoid
    "hallintayksikko" ::hallintayksikko-id
    "harjassa_luotu" ::harjassa-luotu?
    "hanke" ::hanke-id
    "urakoitsija" ::urakoitsija-id
    "takuu_loppupvm" ::takuu-loppupvm
    "ulkoinen_id" ::ulkoinen-id
    "luoja" ::luoja-id
    "muokkaaja" ::muokkaaja-id}])

;; Tarkennetaan määrityksiä
(s/def
  :harja.domain.urakka/tyyppi
  #{:hoito
    :tekniset-laitteet
    :valaistus
    :vesivayla-ruoppaus
    :vesivayla-hoito
    :vesivayla-kanavien-korjaus
    :siltakorjaus
    :paallystys
    :paikkaus
    :tiemerkinta
    :vesivayla-kanavien-hoito
    :vesivayla-turvalaitteiden-korjaus})

(s/def ::urakkatyyppi ::tyyppi) ;; Alias tyypille, jota ainakin tietyöilmoitus käyttää.

;; Haut

(s/def ::hae-harjassa-luodut-urakat-vastaus
  (s/coll-of (s/and ::urakka
                    (s/keys :req [::hallintayksikko ::urakoitsija ::sopimukset ::hanke]))))

;; Urakkakohtainen kysely, joka vaatii vain urakan id:n.
;; Tätä speciä on hyvä käyttää esim. palveluiden, jotka hakevat
;; urakan tietoja, kyselyspecinä.
(s/def ::urakka-kysely (s/keys :req [::id]))

;; Tallennukset

(s/def ::tallenna-urakka-kysely (s/keys :req [::sopimukset ::hallintayksikko ::urakoitsija
                                              ::nimi ::loppupvm ::alkupvm]
                                        :opt [::id]))

(s/def ::tallenna-urakka-vastaus (s/keys :req [::sopimukset ::hallintayksikko ::urakoitsija
                                               ::nimi ::loppupvm ::alkupvm ::id]))

;; Muut

(defn vesivayla-urakka? [urakka]
  (#{:vesivayla-hoito :vesivayla-ruoppaus :vesivayla-turvalaitteiden-korjaus
     :vesivayla-kanavien-hoito :vesivayla-kanavien-korjaus}
    (:tyyppi urakka)))
