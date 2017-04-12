(ns harja.domain.urakka
  "Määrittelee urakka nimiavaruuden specit, jotta urakan tietoja voi käyttää namespacetuilla
  keywordeilla, esim. {:urakka/id 12}"
  (:require [clojure.spec :as s]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            #?@(:clj [[clojure.future :refer :all]])))

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::nimi string?)

(s/def ::alkupvm inst?)
(s/def ::loppupvm inst?)

(s/def ::vuosi (s/and nat-int? #(>= % 1900)))

(s/def ::sampoid string?)

(s/def ::tyyppi #{:hoito :paallystys :valaistus :tiemerkinta
                  :tekniset-laitteet :siltakorjaus :paikkaus})

(s/def ::urakka (s/keys :req [::id]
                        :opt [::nimi ::sampoid ::tyyppi
                              ::alkupvm ::loppupvm
                              ::urakoitsija
                              ::hallintayksikko]))

;; Urakkakohtainen kysely, joka vaatii vain urakan id:n.
;; Tätä speciä on hyvä käyttää esim. palveluiden, jotka hakevat
;; urakan tietoja, kyselyspecinä.
(s/def ::urakka-kysely (s/keys :req [::id]))

(defn vesivayla-urakka? [urakka]
  (#{:vesivayla-hoito :vesivayla-ruoppaus :vesivayla-turvalaitteiden-korjaus
     :vesivayla-kanavien-hoito :vesivayla-kanavien-korjaus}
    (:tyyppi :urakka)))
